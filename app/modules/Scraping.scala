package modules

import akka.actor.Actor
import com.google.inject.{Inject, AbstractModule}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.ws.WSClient

class TeamLoadActor @Inject()(ws: WSClient) extends Actor {
  override def receive: Receive = {
    case _ =>
      println("Huzzah")
      sender() ! "Allah be praised!"
  }
}


class ScrapingModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[TeamLoadActor]("team-load-actor")
  }
}
