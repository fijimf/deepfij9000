package controllers

import javax.inject.Inject

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import controllers.model.{SocialData, LogoUrls, Colors, Team}
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode
import modules.scraping.{TeamDetail, ShortNameAndKeyByStatAndPage, ShortTeamByYearAndConference, ShortTeamAndConferenceByYear}
import play.api.Logger
import play.api.mvc.{Action, Controller}
import play.api.libs.json.{JsString, JsObject, Json}
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{ReactiveMongoComponents, MongoController, ReactiveMongoApi}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}
import reactivemongo.bson.{Macros, BSONDocument, BSONHandler}
import reactivemongo.core.protocol.Query
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

class Loader @Inject()(@Named("data-load-actor") teamLoad: ActorRef, val reactiveMongoApi: ReactiveMongoApi)
                      (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger: Logger = Logger(this.getClass)
  implicit val globalTimeout = Timeout(2.minutes)
  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]

  def loadConferenceMaps = Action {
    import play.modules.reactivemongo.json._

    val aliasMap: Future[Map[String, String]] = loadAliasMap()

    val academicYears: List[Int] = List(2012, 2013,2014,2015)
    val confAlignmentByYear: Map[Int, Map[String, List[String]]] = academicYears.map(yr=> yr->Await.result(conferenceAlignmentByYear(yr),1.minute)).toMap
    val teamNameMap =
      db.collection[BSONCollection]("teams")
        .find[BSONDocument](BSONDocument())
        .cursor()
        .collect[List]()
        .map(p=> p.foldLeft(Map.empty[String, String])((map: Map[String, String], jso: JsObject) => map+(jso.value("name").as[String] -> jso.value("key").as[String])))
    val normalizedMap: Future[Map[Int, Map[String, List[String]]]] = for (
      am<-aliasMap;
      teamNames <- teamNameMap
    ) yield {
      confAlignmentByYear.mapValues(_.mapValues(_.flatMap(s => {
        teamNames.get(s).orElse(am.get(s))
      })))
    }

 val result: Map[Int, Map[String, List[String]]] = Await.result(normalizedMap, 5.minutes)

    Ok(result.mkString("\n"))
  }

  def saveTeams(teams: List[Team]): Future[MultiBulkWriteResult] = {
    def collection: BSONCollection = db.collection[BSONCollection]("teams")

    val f: Unit => Future[MultiBulkWriteResult] = _ => collection.bulkInsert(teams.map(teamHandler.write).toStream, ordered = false)
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

  def loadReferenceData = Action {
    logger.info("Loading preliminary team/conference data.")
//    val academicYears: List[Int] = List(2015, 2014, 2013, 2012)
    val academicYears: List[Int] = List(2015, 2014, 2013, 2012)
    val teamShortNames: Future[Map[String, String]] = masterShortName(List(1, 2, 3, 4, 5, 6, 7), 145)
    //    val confAlignmentByYear: Future[Map[Int, Map[String, List[String]]]] = conferenceAlignmentByYear(academicYears)
    //    for (
    //      tsn <- teamShortNames;
    //      caby <- confAlignmentByYear
    //    ) {
    //      val fromCom: Set[String] = tsn.values.toSet
    //      val fromOrg: Set[String] = caby.values.flatMap(_.values.flatten).toSet
    //      val diff1: Set[String] = fromCom.diff(fromOrg)
    //      val diff2: Set[String] = fromOrg.diff(fromCom)
    //      logger.info("Teams known to com but not org: "+diff1.toString())
    //      logger.info("Teams known to com but not org: "+diff2.toString())
    //
    //    }
    val aliasMap: Future[Map[String, String]] = loadAliasMap()

    val teamShortNames1 = for (
      tsn <- teamShortNames;
      am <- aliasMap
    ) yield {
      logger.info("ALIAS MAP: " + am.keys.mkString(", "))
      tsn.map((tup: (String, String)) => {
        if (am.contains(tup._1)) {
          am(tup._1) -> tup._2
        } else {
          tup
        }
      })
    }
    logger.info("Loading team detail")


    //    val teamMaster: Future[List[Team]] = teamShortNames.flatMap((tsn: Map[String, String]) => {
    //      Future.sequence(tsn.keys.map(k => {
    //        val eventualTeam: Future[Team] = (teamLoad ? TeamDetail( k, tsn(k))).mapTo[Team]
    //        eventualTeam
    //      }))
    //    }).map(_.toList)

    val teamMaster: Future[List[Team]] = teamShortNames1.map((tsn: Map[String, String]) => {
      tsn.keys.grouped(4).map((is: Iterable[String]) => {
        Await.result(Future.sequence(is.map((k: String) => {
          (teamLoad ? TeamDetail(k, tsn(k))).mapTo[Team]
        })), 600.seconds)
      }).flatten.toList
    })


    val s = Await.result(teamMaster, 600.seconds)
    //val r = Await.result(confAlignmentByYear, 300.seconds)
    Ok(s.toString())
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
