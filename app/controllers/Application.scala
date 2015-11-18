package controllers

import javax.inject.Inject

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Application @Inject()(@Named("data-load-actor") teamLoad: ActorRef)
                           (implicit ec: ExecutionContext) extends Controller {
  implicit val timeout = Timeout(105.seconds)

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def other = Action.async {
    (teamLoad ? "Jim").map(s => Ok(views.html.index(s.toString)))
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
