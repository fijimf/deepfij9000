package modules

import java.io.{StringReader, Reader}

import akka.actor.{ActorRef, ActorSystem, Actor}
import com.google.inject.{Inject, AbstractModule}
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.{WSRequest, WSResponse, WS, WSClient}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq, InputSource, Node}
import scala.xml.parsing.NoBindingFactoryAdapter

object HTML {
  def loadReader(r: Reader): Try[Node] = Try {
    new NoBindingFactoryAdapter().loadXML(new InputSource(r), new SAXFactoryImpl().newSAXParser())
  }


  def loadString(s: String): Try[Node] = loadReader(new StringReader(s))
}

class TeamLoadActor @Inject()(ws: WSClient) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  def extractMaps(htmlNode: Node): (Map[Int, String],Map[Int, String]) = {
    val teamMap = extractTeamMap(htmlNode)

    val confMap = extractConferenceMap(htmlNode)
    confMap.foreach(n => println(n))
    teamMap.foreach(n=> println(n))
    (teamMap, confMap)
  }

  def extractConferenceMap(htmlNode: Node): Map[Int, String] = {
    (htmlNode \\ "li" \ "a").filter(_.attribute("href").flatMap(_.headOption).exists(_.text.startsWith("javascript:changeConference"))).map(n => {
      val id = n.attribute("href").flatMap(_.headOption).map(_.text.replace("javascript:changeConference(", "").replace(");", "").toDouble.toInt)
      id -> n.text
    }).filter(_._1.isDefined).map((tup: (Option[Int], String)) => (tup._1.get, tup._2)).toMap

  }

  def extractTeamMap(htmlNode: Node): Map[Int, String] = {
    (htmlNode \\ "td" \ "a").map(n => {
      val id = n.attribute("href").flatMap(_.headOption).map(_.text.replaceAll( """/team/index/\d+\?org_id=""", "").toInt)
      id -> n.text
    }).filter(_._1.isDefined).map(tup => tup._1.get -> tup._2).toMap
  }

  override def receive: Receive = {
    case year: Int =>
      val mySender = sender()
      ws.url(createURL(year)).get().onComplete {
        case Success(response) =>
          HTML.loadString(response.body) match {
            case Success(htmlNode) =>
              mySender ! extractMaps(htmlNode)
            case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
          }
        case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
      }
    case (year:Int, conference:Int) =>
      val mySender = sender()
      ws.url(createURL(year, conference)).get().onComplete {
        case Success(response) =>
          HTML.loadString(response.body) match {
            case Success(htmlNode) =>
              mySender ! extractTeamMap(htmlNode)
            case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
          }
        case Failure(ex) => sender ! "Failed with exception " + ex.getMessage
      }
    case _ =>
      println("Unexpected message")
  }

  def createURL(year: Int, confId: Int = -1): String = {
    "http://stats.ncaa.org/team/inst_team_list?academic_year=" + year + "&conf_id="+confId+"&division=1&sport_code=MBB"
  }
}

//implicit val timeout: Timeout = Timeout(15.seconds)
//implicit val system: ActorSystem = context.system
//import scala.concurrent.ExecutionContext.Implicits.global
//val response = (IO(Http) ? HttpRequest(GET, Uri("http://stats.ncaa.org/team/inst_team_list?academic_year=2015&conf_id=-1&division=1&sport_code=MBB"))).mapTo[HttpResponse]
//response.onComplete(handleResponse)
//case _ => log.warning("Unrecognized message")

class ScrapingModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[TeamLoadActor]("team-load-actor")
  }
}
