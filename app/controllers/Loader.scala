package controllers

import javax.inject.Inject

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import modules.scraping.{ShortNameAndKeyByStatAndPage, ShortTeamByYearAndConference, ShortTeamAndConferenceByYear}
import play.api.Logger
import play.api.mvc.{Action, Controller}
import play.api.libs.json.Json
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class Loader @Inject()(@Named("data-load-actor") teamLoad: ActorRef)
                      (implicit ec: ExecutionContext) extends Controller {
  val logger: Logger = Logger(this.getClass())
  implicit val timeout = Timeout(105.seconds)

  def loadReferenceData = Action {
    val academicYears: List[Int] = List(2015, 2014, 2013, 2012)
    val teamShortNames: Future[Map[String, String]] = masterShortName(List(1, 2, 3, 4, 5, 6, 7), 145)
    val confAlignmentByYear: Future[Map[Int, Map[String, List[String]]]] = conferenceAlignmentByYear(academicYears)
    for (
      tsn <- teamShortNames;
      caby <- confAlignmentByYear
    ) {
      val fromCom: Set[String] = tsn.values.toSet
      val fromOrg: Set[String] = caby.values.map(_.values.flatten).flatten.toSet
      val diff1: Set[String] = fromCom.diff(fromOrg)
      val diff2: Set[String] = fromOrg.diff(fromCom)
      logger.info(diff1.toString())
      logger.info(diff2.toString())

    }
    val s = Await.result(teamShortNames, 300.seconds)
    val r = Await.result(confAlignmentByYear, 300.seconds)
    Ok(Json.toJson(r))
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
          (teamLoad ? ShortTeamByYearAndConference(yr, c)).mapTo[TeamMap].map(_.data.values.toList).map(confMap.getOrElse(c, c.toString) -> _)
        })).map(_.toMap).map(yr -> _)
      })).map(_.toMap)
    })
    confAlignmentByYear
  }

  def masterShortName(pagination: List[Int], stat: Int): Future[Map[String, String]] = {
    pagination.foldLeft(Future.successful(Seq.empty[(String, String)]))((data: Future[Seq[(String, String)]], p: Int) => {
      for (
        t0 <- data;
        t1 <- (teamLoad ? ShortNameAndKeyByStatAndPage(stat, p)).mapTo[Seq[(String, String)]]
      ) yield t0 ++ t1
    }).map(_.toMap)
  }
}
