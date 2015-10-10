package modules

import java.io.{StringReader, Reader}

import akka.actor.{ActorSystem, Actor}
import com.google.inject.{Inject, AbstractModule}
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.{WSResponse, WS, WSClient}

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
  val responseHandler: (Try[WSResponse]) => Map[String, String] = {
    case Success(response) =>
      HTML.loadString(response.body) match {
        case Success(htmlNode) =>
          val seq: NodeSeq = htmlNode \\ "li" \ "a"
          println(seq.size)
          val filter: NodeSeq = seq.filter(_.attribute("href").flatMap(_.headOption).exists(_.text.startsWith("/schools/")))
          val confMap = filter.map(n => {
            val id = n.attribute("href").flatMap(_.headOption).map(_.text.replace("/schools/", ""))
            val name: String = (n \ "span").text
            id -> name
          }).filter(tup => tup._1.isDefined && tup._1.get.length > 1).map(tup => tup._1.get -> tup._2)
          confMap.foreach(n => println(n))
          confMap.toMap
        case Failure(ex) => Map.empty
      }
    case Failure(ex) =>
      println(ex.getMessage)
      Map.empty[String, String]
  }


  override def receive: Receive = {

    case year: Int =>
      ws.url("http://stats.ncaa.org/team/inst_team_list?academic_year=" + year + "&conf_id=-1&division=1&sport_code=MBB").get().onComplete(responseHandler)


    case _ =>
      println("Unexpected message")

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
