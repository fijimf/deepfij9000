package controllers.scrapers

import java.io.File

import akka.actor.Actor
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import play.{api, Mode}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Environment, DefaultApplication, Play}
import play.api.test.WsTestClient
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import org.xml.sax.InputSource

import scala.xml._
import scala.xml.parsing.NoBindingFactoryAdapter


class TeamLoader extends Actor {
  import javax.inject.Inject
  import scala.concurrent.Future

  import play.api.mvc._
  import play.api.libs.ws._


  override def receive: Receive = {
    case _ => {
      import play.api.Play.current
      WS.url("http://stats.ncaa.org/team/inst_team_list?academic_year=2015&conf_id=-1&division=1&sport_code=MBB")
      println("hooray")
    }
  }
}


object Main2 {
  val application = new GuiceApplicationBuilder()
    .in(Environment(new File("."), this.getClass.getClassLoader, api.Mode.Dev))
    .build

  def main(args: Array[String]): Unit = {

    val system = application.actorSystem
    val loader = system.actorOf(Props(classOf[TeamLoader]), "teamLoader")
    implicit val timeout = Timeout(5.seconds)
    loader ! new Character('g')

  }
}


//class MainServiceActor2() extends Actor with HttpService {
//  def actorRefFactory = context
//
//  def receive = runRoute(rte)
//
//  private val staticAssets: Route = pathPrefix("assets") {
//    path("css" / PathMatchers.Segment) { filename =>
//      get {
//        respondWithMediaType(`text/css`) {
//          getFromResource("css/" + filename, `text/css`)
//        }
//      }
//    } ~ path("js" / PathMatchers.Segment) { filename =>
//      get {
//        respondWithMediaType(`application/javascript`) {
//          getFromResource("js/" + filename, `application/javascript`)
//        }
//      }
//    } ~ path("img" / PathMatchers.Segment) { filename =>
//      get {
//        respondWithMediaType(`image/png`) {
//          getFromResource("img/" + filename, `image/png`)
//        }
//      }
//    }
//  }
//
//  val rte =
//    path("fijimf") {
//      get {
//        respondWithMediaType(`text/html`) {
//          // XML is marshalled to `text/xml` by default, so we simply override here
//          complete {
//            <html>
//              <body>
//                <h1>Say hello to
//                  <i>spray-routing</i>
//                  on
//                  <i>spray-can</i>
//                  !</h1>
//              </body>
//            </html>
//          }
//        }
//      }
//    } ~ staticAssets
//}
//
//

//class Discovery2Actor() extends Actor {
//  val log = Logging(context.system, this)
//  var instanceActors = Map.empty[String, ActorRef]
//
//
//  val handleResponse: (Try[HttpResponse]) => Unit = {
//    case Success(resp) => Try {
//      HTML.loadString(resp.entity.data.asString)
//
//    } match {
//      case Success(htmlNode) =>
//        val seq: NodeSeq = htmlNode \\ "li" \ "a"
//        println(seq.size)
//        val filter: NodeSeq = seq.filter(_.attribute("href").flatMap(_.headOption).exists(_.text.startsWith("/schools/")))
//        val confMap = filter.map(n=>{
//          val id = n.attribute("href").flatMap(_.headOption).map(_.text.replace( "/schools/",""))
//          val name: String = (n \ "span").text
//          id -> name
//        }).filter(tup=> tup._1.isDefined && tup._1.get.length>1).map(tup=>tup._1.get->tup._2)
//        confMap.foreach(n => println(n))
//
//      case Failure(ex) =>
//        log.warning("Failed parsing discovery service response")
//        log.warning("Response was: " + resp.entity.data.toString)
//    }
//    case Failure(ex) => log.error("Failed to retrieve instances from Discovery Service")
//  }
//
//
//  override def receive: Receive = {
//    case c:Character =>
//      implicit val timeout: Timeout = Timeout(15.seconds)
//      implicit val system: ActorSystem = context.system
//      import scala.concurrent.ExecutionContext.Implicits.global
//      val response = (IO(Http) ? HttpRequest(GET, Uri("http://www.ncaa.com/schools/"+c))).mapTo[HttpResponse]
//      response.onComplete(handleResponse)
//    case _ => log.warning("Unrecognized message")
//  }
