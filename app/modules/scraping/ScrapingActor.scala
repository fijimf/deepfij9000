package modules.scraping

import java.util.concurrent.Executors

import akka.actor.Actor
import com.google.inject.Inject
import modules.scraping.requests.{HtmlScrapeRequest, JsonScrapeRequest}
import modules.scraping.util.HTML
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class ScrapingActor @Inject()(ws: WSClient) extends Actor {
  val logger: Logger = Logger(this.getClass)
  implicit val ec = new ExecutionContext {
    val threadPool = Executors.newFixedThreadPool(36) // Limited so ncaa.com stops blocking me

    def execute(runnable: Runnable) {
      threadPool.submit(runnable)
    }

    def reportFailure(t: Throwable) {}
  }

  override def receive: Receive = {
    case r:HtmlScrapeRequest[_] =>
        handleScrape(r)
    case r:JsonScrapeRequest[_] =>
        handleJsonScrape(r)
    case _ =>
      println("Unexpected message")
  }

  def handleScrape(r: HtmlScrapeRequest[_]): Unit = {
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


def handleJsonScrape(r: JsonScrapeRequest[_]): Unit = {
  val mySender = sender()
  logger.info("Requesting " + r.url)
  ws.url(r.url).get().onComplete {
    case Success(response) =>
      if (response.status == 200) {
        logger.info("%d - %s (%d bytes)".format(response.status, response.statusText, response.body.length))
      } else {
        logger.warn("%d - %s (%s )".format(response.status, response.statusText, r.url))
      }
      if (response.body.length < 10) {
        mySender ! "Failed with empty body"
      } else {
        Try {
          Json.parse(r.preProcessBody(response.body))
        } match {
          case Success(js) =>
            mySender ! r.scrape(js)
          case Failure(ex) =>
            logger.error("Error parsing response:\n" + ex.getMessage + " " + response.body)
            mySender ! "Failed with exception " + ex.getMessage
        }
      }
    case Failure(ex) =>
      logger.error("Get failed", ex)
      mySender ! "Failed with exception " + ex.getMessage
  }
}
}

