package modules.scraping

import akka.actor.Actor
import com.google.inject.Inject
import controllers.{TeamConfMap, TeamMap, TeamMaster, TeamMasterRec}
import play.api.Logger
import play.api.libs.ws.WSClient

import scala.util.{Failure, Success}
import scala.xml.Node

class ScrapingActor @Inject()(ws: WSClient) extends Actor {
  val logger: Logger = Logger(this.getClass())
  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Receive = {
    case r:ScrapeRequest[_] =>
      val mySender = sender()
      logger.info("Requesting "+r.url)
      ws.url(r.url).get().onComplete {
        case Success(response) =>
          logger.info("%d - %s (%d bytes)".format(response.status, response.statusText, response.body.length))
          if (response.status!=200){
            logger.info(r.url)
          }
          HTML.loadString(response.body) match {
            case Success(node)=>
              mySender ! r.scrape(node)
            case Failure(ex) =>
              logger.error("Error parsing response:\n"+response.body, ex)
              sender ! "Failed with exception " + ex.getMessage
          }
        case Failure(ex) =>
          logger.error("Get failed", ex)
          sender ! "Failed with exception " + ex.getMessage
      }
    case _ =>
      println("Unexpected message")
  }
}
