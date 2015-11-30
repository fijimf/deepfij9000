package controllers

import javax.inject.Inject

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import controllers.model._
import modules.scraping._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{UpdateWriteResult, MultiBulkWriteResult}
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Loader @Inject()(@Named("data-load-actor") teamLoad: ActorRef, val reactiveMongoApi: ReactiveMongoApi)
                      (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger: Logger = Logger(this.getClass)
  implicit val globalTimeout = Timeout(2.minutes)
  val academicYears: List[Int] = List(2012, 2013, 2014, 2015, 2016)
  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, LocalDate] {
    def read(time: BSONDateTime) = new LocalDate(time.value)

    def write(jdtime: LocalDate) = BSONDateTime(jdtime.toDate.getTime)
  }

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

  def loadConferenceMaps = Action.async {
    import play.modules.reactivemongo.json._

    val aliasMap: Future[Map[String, String]] = loadAliasMap()


    val confAlignmentByYear: Map[Int, Map[String, List[String]]] = academicYears.map(yr => {
      yr -> aacToBigEastHack(Await.result(conferenceAlignmentByYear(yr), 1.minute))
    }).toMap

    val teamNameMap =
      db.collection[BSONCollection]("teams")
        .find(BSONDocument()).cursor[Team].collect[List]()
        .map(p => p.foldLeft(Map.empty[String, String])((map: Map[String, String], team: Team) => map + (team.name -> team.key)))
    val normalizedMap: Future[Map[Int, Map[String, List[String]]]] = for (
      am <- aliasMap;
      teamNames <- teamNameMap
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

    seasons.flatMap(saveSeasons).map(wr => {
      Ok(wr.toString)
    })

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
    val upd = BSONDocument("games" -> games)
    collection.update(sel, upd, upsert = true)
  }

  def saveTeams(teams: List[Team]): Future[MultiBulkWriteResult] = {
    def collection: BSONCollection = db.collection[BSONCollection]("teams")
    collection.drop().flatMap(Unit => collection.bulkInsert(teams.map(teamHandler.write).toStream, ordered = false))
  }

  def loadTeams = Action.async {
    logger.info("Loading preliminary team keys.")
    val teamShortNames: Future[Map[String, String]] = masterShortName(List(1, 2, 3, 4, 5, 6, 7), 145)
    val aliasMap: Future[Map[String, String]] = loadAliasMap()

    val teamShortNames1 = for (
      tsn <- teamShortNames;
      am <- aliasMap
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
    teamMaster.flatMap(saveTeams).map(wr => {
      Ok(wr.toString)
    })
  }

  object DateIterator {
    def apply(s: LocalDate, e: LocalDate): Iterator[LocalDate] = new Iterator[LocalDate] {
      var d = s

      override def hasNext: Boolean = d.isBefore(e)

      override def next(): LocalDate = {
        d = d.plusDays(1)
        d
      }
    }
  }

  def loadGames = Action.async {
    loadTeamsFromDb().flatMap(tl => {
      loadAliasMap().flatMap(am => {
        val keySet: Set[String] = tl.map(_.key).toSet

        val gameData: Map[Int, List[GameData]] = academicYears.map(y => y -> {
          DateIterator(new LocalDate(y-1, 11, 1), new LocalDate(y, 4, 30)).grouped(10).map(ds => {
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
    masterTeamConference.flatMap(tcm => {
      val confMap: Map[Int, String] = tcm.cm.data
      Future.sequence(confMap.keys.map(c => {
        val future = (teamLoad ? ShortTeamByYearAndConference(yr, c)).mapTo[TeamMap]
        future.map(_.data.values.toList).map(confMap.getOrElse(c, c.toString) -> _)
      })).map(_.toMap)
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

  def loadAliasMap(): Future[Map[String, String]] = {
    import play.modules.reactivemongo.json._

    val aliasCollection = db.collection[JSONCollection]("aliases")
    aliasCollection.find(Json.obj()).cursor[JsObject](ReadPreference.primaryPreferred).collect[List]().map(list => {
      list.map(jso => {
        val a: String = jso.value("alias").as[String]
        val k: String = jso.value("key").as[String]
        a -> k
      }).toMap
    })
  }

  def loadTeamsFromDb(): Future[List[Team]] = {
    import play.modules.reactivemongo.json._


    val teamCollection = db.collection[BSONCollection]("teams")
    teamCollection.find(BSONDocument()).cursor[Team](ReadPreference.primaryPreferred).collect[List]().mapTo[List[Team]]

  }
}
