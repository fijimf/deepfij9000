package controllers

import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import controllers.model.{Colors, LogoUrls, Team}
import modules.scraping.{ShortTeamAndConferenceByYear, LongNameAndKeyByInitial}
import play.api._
import play.api.libs.iteratee.{Input, Step, Iteratee, Enumerator}
import play.api.mvc._
import scala.concurrent.duration._

import scala.concurrent.{Await, Future, ExecutionContext}
import scala.util.{Failure, Success}

class Application @Inject()(@Named("team-load-actor") teamLoad: ActorRef)
                           (implicit ec: ExecutionContext) extends Controller {
  implicit val timeout = Timeout(105.seconds)

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def other = Action.async {
    (teamLoad ? "Jim").map(s => Ok(views.html.index(s.toString)))
  }

  def scrape = Action {
    val future: Future[TeamConfMap] = List(2015, 2014, 2013, 2012).foldLeft(Future.successful(TeamConfMap()))((f: Future[TeamConfMap], yr: Int) => {
      for (
        t0 <- f;
        t1 <- (teamLoad ? ShortTeamAndConferenceByYear(yr)).mapTo[TeamConfMap]
      ) yield t0.merge(t1)
    })


    val r = Await.result(future, 300.seconds)
    Ok("Done " + r.cm.data.size + " conferences; " + r.tm.data.size + " teams;")
  }

  def teamPageMaster = Action {

    val future: Future[TeamMaster] = "abcdefghijklmnopqrstuvwxyz".foldLeft(Future.successful(TeamMaster()))((f: Future[TeamMaster], c: Char) => {
      for (
        t0 <- f;
        t1 <- (teamLoad ? LongNameAndKeyByInitial(c)).mapTo[TeamMaster]
      ) yield t0.merge(t1)
    })

    val r = Await.result(future, 300.seconds)
    Ok("Done" + r)
  }

//  def createNcaaOrgScraper(years:List[Int]):Enumerator[TeamConfMap] = {
//
//    new Enumerator[TeamConfMap] {
//      override def apply[List[Int]](i: Iteratee[TeamConfMap, List[Int]]): Future[Iteratee[TeamConfMap, List[Int]]] =  {
//
//          i.fold {
//            case Step.Done(result, remaining) => Future(i)
//            case Step.Cont(k: (Input[TeamConfMap] => Iteratee[TeamConfMap, List[Int]])) => {
//              if (index < items.size) {
//                val item = items(index)
//                println(s"El($item)")
//                index += 1
//                val newIteratee = k(Input.El(item))
//                apply(newIteratee)
//              } else {
//
//                Future(k(Input.EOF))
//              }
//            }
//
//            // iteratee is in error state
//            case Step.Error(message, input: Input[TeamConfMap]) => Future(i)
//      }
//
//    }
//  }


  def team = Action {
    Ok(views.html.teamView(Team(
      "georgetown",
      "Georgetown",
      None,
      Some("Hoyas"),
      Some(LogoUrls(
        Some("http://i.turner.ncaa.com/dr/ncaa/ncaa7/release/sites/default/files/images/logos/schools/w/william-mary.40.png"),
        Some("http://i.turner.ncaa.com/dr/ncaa/ncaa7/release/sites/default/files/images/logos/schools/w/william-mary.70.png")
      )
      ), Some(Colors(Some("#116633"), None)))))

  }


}


case class TeamConfMap(cm: ConferenceMap = ConferenceMap(), tm: TeamMap = TeamMap()) {
  def merge(tcm: TeamConfMap): TeamConfMap = {
    TeamConfMap(cm.merge(tcm.cm), tm.merge(tcm.tm))
  }
}

case class ConferenceMap(data: Map[Int, String] = Map.empty) {
  def merge(cm: ConferenceMap): ConferenceMap = {
    ConferenceMap(data ++ cm.data)
  }
}

case class TeamMap(data: Map[Int, String] = Map.empty) {
  def merge(tm: TeamMap): TeamMap = {
    TeamMap(data ++ tm.data)
  }
}

case class TeamMaster(teams:List[TeamMasterRec]=List.empty[TeamMasterRec]) {
  val byKey = teams.map(r=>r.key-> r.name).toMap
  val byName = teams.map(r=>r.name-> r.key).toMap
  def merge(tm:TeamMaster):TeamMaster = {
    TeamMaster(teams++tm.teams)
  }
}

sealed trait NcaaOrgScrapeStatus

case class TeamMasterRec(key:String, name:String)
