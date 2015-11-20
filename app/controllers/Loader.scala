package controllers

import javax.inject.Inject

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import controllers.model._
import modules.scraping._
import org.joda.time.{LocalDate, LocalDateTime}
import play.api.Logger
import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsString, JsObject, Json}
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{ReactiveMongoComponents, MongoController, ReactiveMongoApi}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}
import reactivemongo.bson.{BSONDateTime, Macros, BSONDocument, BSONHandler}
import reactivemongo.core.protocol.Query
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

class Loader @Inject()(@Named("data-load-actor") teamLoad: ActorRef, val reactiveMongoApi: ReactiveMongoApi)
                      (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger: Logger = Logger(this.getClass)
  implicit val globalTimeout = Timeout(2.minutes)

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, LocalDateTime] {
    def read(time: BSONDateTime) = new LocalDateTime(time.value)

    def write(jdtime: LocalDateTime) = BSONDateTime(jdtime.toDate.getTime)
  }

  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
  implicit val resultHandler: BSONHandler[BSONDocument, Result] = Macros.handler[Result]
  implicit val gameHandler: BSONHandler[BSONDocument, Game] = Macros.handler[Game]
  implicit val conferenceMembershipHandler: BSONHandler[BSONDocument, ConferenceMembership] = Macros.handler[ConferenceMembership]
  implicit val seasonHandler: BSONHandler[BSONDocument, Season] = Macros.handler[Season]

  def loadConferenceMaps = Action.async {
    import play.modules.reactivemongo.json._

    val aliasMap: Future[Map[String, String]] = loadAliasMap()

    val academicYears: List[Int] = List(2012, 2013, 2014, 2015, 2016)
    val confAlignmentByYear: Map[Int, Map[String, List[String]]] = academicYears.map(yr => {
      yr -> aacToBigEastHack(Await.result(conferenceAlignmentByYear(yr), 1.minute))
    }).toMap

    val teamNameMap =
      db.collection[BSONCollection]("teams")
        .find[BSONDocument](BSONDocument())
        .cursor()
        .collect[List]()
        .map(p => p.foldLeft(Map.empty[String, String])((map: Map[String, String], jso: JsObject) => map + (jso.value("name").as[String] -> jso.value("key").as[String])))
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

      override def next(): LocalDate ={
        d=d.plusDays(1)
        d
      }
    }
  }

  def loadGames = Action.async {
    val years = List(2015)
    Future.sequence(
      years.flatMap(y => {
        DateIterator(new LocalDate(y, 11, 15), new LocalDate(y , 11, 20)).map(d => {
          (teamLoad ? ScoreboardByDate(d)).mapTo[List[GameData]]
        })
      })
    ).map(s => {
      Ok(s.toString)
    })
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
}
