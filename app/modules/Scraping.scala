package modules

import java.io.{StringReader, Reader}

import akka.actor.{ActorRef, ActorSystem, Actor}
import com.google.inject.{Inject, AbstractModule}
import controllers.{TeamMasterRec, TeamMaster, TeamMap, TeamConfMap}
import modules.scraping.{ScrapingActor, HTML}
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.{WSRequest, WSResponse, WS, WSClient}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml.{NodeSeq, InputSource, Node}
import scala.xml.parsing.NoBindingFactoryAdapter

class ScrapingModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[ScrapingActor]("data-load-actor")
  }
}
