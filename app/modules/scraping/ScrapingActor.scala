package modules.scraping

import java.util.concurrent.Executors

import akka.actor.Actor
import com.google.inject.Inject
import controllers.{TeamConfMap, TeamMap, TeamMaster, TeamMasterRec}
import play.api.Logger
import play.api.libs.ws.WSClient

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}
import scala.xml.Node

class ScrapingActor @Inject()(ws: WSClient) extends Actor {
  val logger: Logger = Logger(this.getClass())
  implicit val ec = new ExecutionContext {
    val threadPool = Executors.newFixedThreadPool(36) // Limited so ncaa.com stops blocking me

    def execute(runnable: Runnable) {
      threadPool.submit(runnable)
    }

    def reportFailure(t: Throwable) {}
  }

  override def receive: Receive = {
    case r:ScrapeRequest[_] =>

        handleScrape(r)

    case _ =>
      println("Unexpected message")
  }

  def handleScrape(r: ScrapeRequest[_]): Unit = {
    val mySender = sender()
    logger.info("Requesting " + r.url)
    ws.url(r.url).get().onComplete {
      case Success(response) =>
        if (response.status == 200) {
          logger.info("%d - %s (%d bytes)".format(response.status, response.statusText, response.body.length))
        } else {
          logger.warn("%d - %s (%s )".format(response.status, response.statusText, r.url))
        }
        HTML.loadString(response.body) match {
          case Success(node) =>
            mySender ! r.scrape(node)
          case Failure(ex) =>
            logger.error("Error parsing response:\n" + response.body, ex)
            sender ! "Failed with exception " + ex.getMessage
        }
      case Failure(ex) =>
        logger.error("Get failed", ex)
        sender ! "Failed with exception " + ex.getMessage
    }
  }
}
