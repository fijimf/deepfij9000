package controllers

import javax.inject.Inject

import _root_.util.DateIterator
import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import controllers.analysis.{Analyzer, ScoringModel, WonLostModel}
import controllers.dao.{AliasDao, TeamDao}
import models._
import modules.scraping.model.{GameData, TeamConfMap, TeamMap}
import modules.scraping.requests._
import modules.scraping.util.{ConferenceTourneySolver, NeutralSiteSolver}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{LocalDate, LocalTime}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, UpdateWriteResult, WriteResult}
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Loader @Inject()(@Named("data-load-actor") teamLoad: ActorRef, val reactiveMongoApi: ReactiveMongoApi)
                      (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger: Logger = Logger(this.getClass)
  implicit val globalTimeout = Timeout(2.minutes)
  val academicYears: List[Int] = List(2013, 2014, 2015, 2016)

  val teamDao = TeamDao(db)
  val aliasDao = AliasDao(db)

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, LocalDate] {
    def read(time: BSONDateTime) = new LocalDate(time.value)

    def write(jdtime: LocalDate) = BSONDateTime(jdtime.toDate.getTime)
  }

  implicit val modelResultHandler: BSONHandler[BSONDocument, ModelResult] = Macros.handler[ModelResult]
  implicit val modelSeriesHandler: BSONHandler[BSONDocument, ModelPop] = Macros.handler[ModelPop]
  implicit val modelValueHandler: BSONHandler[BSONDocument, ModelValue] = Macros.handler[ModelValue]
  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
  implicit val resultHandler: BSONHandler[BSONDocument, Result] = Macros.handler[Result]
  implicit val tourneyInfoHandler: BSONHandler[BSONDocument, TourneyInfo] = Macros.handler[TourneyInfo]
  implicit val gameHandler: BSONHandler[BSONDocument, Game] = Macros.handler[Game]
  implicit val conferenceMembershipHandler: BSONHandler[BSONDocument, ConferenceMembership] = Macros.handler[ConferenceMembership]
  implicit val seasonHandler: BSONHandler[BSONDocument, Season] = Macros.handler[Season]
  implicit val reader: BSONDocumentReader[Team] = Macros.reader[Team]
  implicit val seasonReader: BSONDocumentReader[Season] = Macros.reader[Season]
  implicit val modelResultReader: BSONDocumentReader[ModelResult] = Macros.reader[ModelResult]
  implicit val modelResultWriter: BSONDocumentWriter[ModelResult] = Macros.writer[ModelResult]

  import play.api.Play.current

  import scala.concurrent.duration._

  var millisUntilFourAM = ((new LocalTime(5, 0, 0).getMillisOfDay - new LocalTime().getMillisOfDay) + (1000 * 60 * 60 * 24)) % (1000 * 60 * 60 * 24)


  Akka.system.scheduler.schedule(millisUntilFourAM.milliseconds, 24.hours) {
    logger.info("Loading todays results")
    val today = new LocalDate()
    loadGames(today.minusDays(2), today.plusDays(6), 2016)
    saveModels(List(WonLostModel, ScoringModel), 2016)
  }

  def runModelsNow = Action.async {
    saveModels(List(WonLostModel, ScoringModel), 2016).map(lwr => {
      Ok(lwr.map(_.message).mkString("\n"))
    })
  }

  def loadConferenceMaps = Action.async {
    val seasons: Future[List[Season]] = extractConferenceMap // TODO make this better

    seasons.flatMap(saveSeasons).map(wr => {
      Ok(wr.toString)
    })

  }

  def extractConferenceMap: Future[List[Season]] = {
    val confAlignmentByYear: Map[Int, Map[String, List[String]]] = academicYears.map(yr => {
      yr -> aacToBigEastHack(Await.result(conferenceAlignmentByYear(yr), 1.minute))
    }).toMap


    val normalizedMap: Future[Map[Int, Map[String, List[String]]]] = for (
      am <- aliasDao.loadAliasMap();
      teamNames <- teamDao.loadTeamNameMap()
    ) yield {
      confAlignmentByYear.mapValues(_.mapValues(_.flatMap(s => {
        teamNames.get(s).orElse(am.get(s))
      })))
    }
    val seasons = normalizedMap.map(_.map(tup => {
      val year = tup._1
      val confToTeam = tup._2
      val teamMap: List[ConferenceMembership] = confToTeam.flatMap((tup2: (String, List[String])) => {
        val confName: String = tup2._1
        val teamList: List[String] = tup2._2
        teamList.map((s: String) => {
          ConferenceMembership(s, confName)
        })
      }).toList
      Season(year, List.empty[Game], teamMap)
    }).toList)
    seasons
  }

  def aacToBigEastHack(confMap: Map[String, List[String]]): Map[String, List[String]] = {
    confMap.get("Big East") match {
      case Some(c) => confMap
      case None =>
        val realBE = confMap.getOrElse("AAC", List.empty)
        (confMap - "AAC") + ("Big East" -> realBE)
    }
  }

  def saveSeasons(seasons: List[Season]): Future[MultiBulkWriteResult] = {
    def collection: BSONCollection = db.collection[BSONCollection]("seasons")
    collection.drop().flatMap(Unit => collection.bulkInsert(seasons.map(seasonHandler.write).toStream, ordered = false))
  }

  def updateSeasonGames(academicYear: Int, games: List[Game]): Future[UpdateWriteResult] = {
    def collection: BSONCollection = db.collection[BSONCollection]("seasons")
    val sel = BSONDocument("academicYear" -> academicYear)
    val upd = BSONDocument("$set" -> BSONDocument("games" -> games))
    collection.update(sel, upd, upsert = false)
  }



  def loadTeams = Action.async {
    logger.info("Loading preliminary team keys.")
    val teamShortNames: Future[Map[String, String]] = masterShortName(List(1, 2, 3, 4, 5, 6, 7), 145)
    val teamShortNames1 = for (
      tsn <- teamShortNames;
      am <- aliasDao.loadAliasMap()
    ) yield {
      logger.info("Loaded alias map: " + am.keys.mkString(", "))
      tsn.map((tup: (String, String)) => {
        if (am.contains(tup._1)) {
          am(tup._1) -> tup._2
        } else {
          tup
        }
      })
    }

    logger.info("Loading team detail")

    val teamMaster: Future[List[Team]] = teamShortNames1.map((tsn: Map[String, String]) => {
      tsn.keys.grouped(4).map((is: Iterable[String]) => {
        Await.result(Future.sequence(is.map((k: String) => {
          (teamLoad ? TeamDetail(k, tsn(k))).mapTo[Team]
        })), 600.seconds)
      }).flatten.toList
    })
    teamMaster.flatMap(teamDao.saveTeams).map(wr => {
      Ok(wr.toString)
    })
  }


  def updateGames(academicYear: Int, from: LocalDate, to: LocalDate, games: List[Game]): Future[String] = {
    def collection: BSONCollection = db.collection[BSONCollection]("seasons")
    val selector = BSONDocument("academicYear" -> academicYear)
    val future: Future[Option[Season]] = collection.find(selector).one[Season](ReadPreference.primaryPreferred)

    future.flatMap {
      case Some(season) =>
        val newGameList = DateIterator(from, to).foldLeft[List[Game]](season.games)((gs: List[Game], dt: LocalDate) => {
          gs.filter(_.date != dt) ++ games.filter(_.date == dt)
        })
        val eventualWriteResult = collection.update(selector, BSONDocument("$set" -> BSONDocument("games" -> newGameList)))
        eventualWriteResult.map(_.toString)
      case None =>
        Future.successful("Season not found")
    }
  }

  def updateGames(academicYear: String, yyyymmdd: String) = Action.async {
    val today = DateTimeFormat.forPattern("yyyyMMdd").parseLocalDate(yyyymmdd)
    loadGames(today.minusDays(2), today.plusDays(7), academicYear.toInt).map(res => Ok(res.toString))
  }

  def loadGames(from: LocalDate, to: LocalDate, season: Int): Future[String] = {
    teamDao.loadAll().flatMap(tl => {
      aliasDao.loadAliasMap().flatMap(am => {
        val keySet: Set[String] = tl.map(_.key).toSet
        val gameData: List[GameData] = DateIterator(from, to).grouped(10).map(ds => {
          getGameData(ds).flatten
        }).flatten.toList
        logger.info("Aliases:\n" + am.mkString("\n"))
        val normalizedGameData: List[GameData] = updateMissingTeamKeyByAlias(gameData, keySet, am)
        val kept = normalizedGameData.filter(gd => keySet.contains(gd.homeTeamKey) && keySet.contains(gd.awayTeamKey))
        val games = kept.map(gd => Game.fromGameData(gd))
        updateGames(season, from, to, games)
      })
    })
  }

  def updateMissingTeamKeyByAlias(gameData: List[GameData], keySet: Set[String], aliasMap: Map[String, String]): List[GameData] = {
    gameData.map(gd => {
      if (!keySet.contains(gd.homeTeamKey)) {
        aliasMap.get(gd.homeTeamKey) match {
          case Some(teamKey) => gd.copy(homeTeamKey = teamKey)
          case None => gd
        }
      } else {
        gd
      }
    }).map(gd => {
      if (!keySet.contains(gd.awayTeamKey)) {
        aliasMap.get(gd.awayTeamKey) match {
          case Some(teamKey) => gd.copy(awayTeamKey = teamKey)
          case None => gd
        }
      } else {
        gd
      }
    })
  }


  def loadGames = Action.async {
    teamDao.loadAll().flatMap(tl => {
      aliasDao.loadAliasMap().flatMap(am => {
        val keySet: Set[String] = tl.map(_.key).toSet

        val gameData: Map[Int, List[GameData]] = academicYears.map(y => y -> {
          DateIterator(new LocalDate(y - 1, 11, 1), new LocalDate(y, 4, 30)).grouped(10).map(ds => {
            getGameData(ds).flatten
          }).flatten
        }.toList).toMap

        logger.info("Aliases:\n" + am.mkString("\n"))
        val normalizedGameData = gameData.mapValues(_.map(gd => {
          if (!keySet.contains(gd.homeTeamKey)) {
            am.get(gd.homeTeamKey) match {
              case Some(teamKey) => gd.copy(homeTeamKey = teamKey)
              case None => gd
            }
          } else {
            gd
          }
        }).map(gd => {
          if (!keySet.contains(gd.awayTeamKey)) {
            am.get(gd.awayTeamKey) match {
              case Some(teamKey) => gd.copy(awayTeamKey = teamKey)
              case None => gd
            }
          } else {
            gd
          }
        }))

        normalizedGameData.keys.foreach(k => {
          val missingTeams: Map[String, Int] = normalizedGameData(k).foldLeft(Map.empty[String, Int])((counts: Map[String, Int], gd: GameData) => {
            def countKey(ss: String, d: Map[String, Int]): Map[String, Int] = {
              if (keySet.contains(ss)) {
                d
              } else {
                d + (ss -> (d.getOrElse(ss, 0) + 1))
              }
            }
            countKey(gd.awayTeamKey, countKey(gd.homeTeamKey, counts))
          })
          logger.info("The following teams (seen twice or more) are unknown:\n" + missingTeams.toList.filter(_._2 > 2).sortBy(-_._2).mkString("\n"))
        })
        val enrichedGameData: Map[Int, List[(GameData, Boolean, Boolean)]] = normalizedGameData.mapValues(ngd => NeutralSiteSolver(ConferenceTourneySolver(ngd)))



        val kept = enrichedGameData.mapValues(_.filter(gd => keySet.contains(gd._1.homeTeamKey) && keySet.contains(gd._1.awayTeamKey)))

        val response: Future[String] = Future.sequence(kept.keys.map(k => {
          val games = kept(k).map(gd => Game.fromGameData(gd._1, gd._2, gd._3))
          updateSeasonGames(k, games)
        })).map(_.map(_.toString).mkString("\n"))
        response.map(s => Ok(s))
      })
    })

  }

  def getGameData(dates: Seq[LocalDate]): Seq[List[GameData]] = {
    Await.result(Future.sequence(dates.map(scrapeOneDay)), 6.minutes)
  }

  def scrapeOneDay: (LocalDate) => Future[List[GameData]] = {
    d => {
      (teamLoad ? ScoreboardByDate(d)).map {
        case lst: List[GameData] =>
          logger.info("For date " + d + ", " + lst.size + " game candidates found")
          lst
        case errMsg: String => logger.info("For date " + d + ", " + errMsg)
          List.empty[GameData]
        case _ => logger.info("For date " + d + ", unexpected response from scraper.")
          List.empty[GameData]
      }
    }
  }

  def conferenceAlignmentByYear(academicYears: List[Int]): Future[Map[Int, Map[String, List[String]]]] = {
    val masterTeamConference: Future[TeamConfMap] = academicYears.foldLeft(Future.successful(TeamConfMap()))((f: Future[TeamConfMap], yr: Int) => {
      for (
        t0 <- f;
        t1 <- (teamLoad ? ShortTeamAndConferenceByYear(yr)).mapTo[TeamConfMap]
      ) yield t0.merge(t1)
    })

    val confAlignmentByYear = masterTeamConference.flatMap(tcm => {
      Future.sequence(academicYears.map(yr => {
        val confMap: Map[Int, String] = tcm.cm.data
        Future.sequence(confMap.keys.map(c => {
          val future: Future[Any] = teamLoad ? ShortTeamByYearAndConference(yr, c)
          future.mapTo[TeamMap].map(_.data.values.toList).map(confMap.getOrElse(c, c.toString) -> _)
        })).map(_.toMap).map(yr -> _)
      })).map(_.toMap)
    })
    confAlignmentByYear
  }

  def conferenceAlignmentByYear(yr: Int): Future[Map[String, List[String]]] = {
    val masterTeamConference: Future[TeamConfMap] = (teamLoad ? ShortTeamAndConferenceByYear(yr)).mapTo[TeamConfMap]
    masterTeamConference.map(tcm => {
      val confMap: Map[Int, String] = tcm.cm.data
      confMap.keys.map(conferenceKey => {
        val conferenceName: String = confMap.getOrElse(conferenceKey, conferenceKey.toString)
        val teamMap: TeamMap = Await.result((teamLoad ? ShortTeamByYearAndConference(yr, conferenceKey)).mapTo[TeamMap], 60.seconds)
        conferenceName -> teamMap.data.values.toList
      }).toMap
    })
  }

  def masterShortName(pagination: List[Int], stat: Int): Future[Map[String, String]] = {
    pagination.foldLeft(Future.successful(Seq.empty[(String, String)]))((data: Future[Seq[(String, String)]], p: Int) => {
      for (
        t0 <- data;
        t1 <- (teamLoad ? ShortNameAndKeyByStatAndPage(stat, p)).mapTo[Seq[(String, String)]]
      ) yield t0 ++ t1
    }).map(_.toMap)
  }



  def loadSeasonFromDb(academicYear: Int): Future[Option[Season]] = {
    val seasonCollection = db.collection[BSONCollection]("seasons")
    seasonCollection.find(BSONDocument("academicYear" -> academicYear)).cursor[Season](ReadPreference.primaryPreferred).headOption
  }

  def saveModels(analyzers: List[Analyzer], academicYear: Int): Future[List[WriteResult]] = {
    val modelCollection: BSONCollection = db.collection[BSONCollection]("modelResults")

    runModels(analyzers, academicYear).flatMap(ml => {
      val lfwr: List[Future[WriteResult]] = ml.map(modr => {
        val selector = BSONDocument("name" -> modr.name)
        val future: Future[Option[ModelResult]] = modelCollection.find(selector).one[ModelResult](ReadPreference.primaryPreferred)

        future.flatMap {
          case Some(modelResult) =>
            logger.info("Updating " + modr.name)
            modelCollection.update(selector, BSONDocument("$set" -> BSONDocument("series" -> modr.series)))

          case None =>
            logger.info("Saving " + modr.name)
            modelCollection.insert(modr)

        }
      })
      Future.sequence(lfwr)
    })
  }

  def runModels(analyzers: List[Analyzer], academicYear: Int): Future[List[ModelResult]] = {
    loadSeasonFromDb(2016).map {

      case Some(s) =>
        logger.info("Loaded season")
        analyzers.flatMap(az => {
          logger.info(az.toString)
          az(s).map { case (label, hib, isNum, fn) =>
            logger.info(label)
            ModelResult(label, hib, isNum, byDates(s, isNum, fn))
          }
        })
      case None =>
        logger.info("Could not find season")
        List.empty
    }
  }

  def byDates(s: Season, isNum: Boolean, fn: (String, LocalDate) => Option[Any]): List[ModelPop] =
    s.allDates.map(d => {
      logger.info("Date is " + d.toString())
      ModelPop(d, byTeams(s, isNum, fn, d))
    })

  def byTeams(s: Season, isNum: Boolean, fn: (String, LocalDate) => Option[Any], d: LocalDate): List[ModelValue] = {
    val teams: List[String] = s.allTeams

    teams.map(t => t -> fn(t, d)).filter(_._2.isDefined).map {
      case (t, Some(v)) => v match {
        case n: Integer => ModelValue(t, n.toDouble)
        case x: Double => ModelValue(t, x)
        case s: String => ModelValue(t, s)
        case q: Any => ModelValue(t, q.toString)
      }
      case _ => throw new RuntimeException()
    }
  }
}
