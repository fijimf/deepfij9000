package modules.scraping

import akka.actor.Actor
import com.google.inject.Inject
import controllers.{TeamConfMap, TeamMap, TeamMaster, TeamMasterRec}
import play.api.libs.ws.WSClient

import scala.util.{Failure, Success}
import scala.xml.Node

class ScrapingActor @Inject()(ws: WSClient) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Receive = {
    case r:ScrapeRequest[_] =>
      val mySender = sender()
      ws.url(r.url).get().onComplete {
        case Success(response) =>
          HTML.loadString(response.body) match {
            case Success(node)=>
              mySender ! r.scrape(node)
            case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
          }
        case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
      }
    case _ =>
      println("Unexpected message")
  }
}
