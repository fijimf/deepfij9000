package controllers

import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import com.google.inject.name.Named
import play.api._
import play.api.mvc._
import scala.concurrent.duration._

import scala.concurrent.{Future, ExecutionContext}

class Application @Inject() (@Named("team-load-actor") teamLoad: ActorRef)
                            (implicit ec: ExecutionContext) extends Controller {
  implicit val timeout = Timeout(5.seconds)
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def other = Action.async {
    (teamLoad ? "Jim").map(s=> Ok(views.html.index(s.toString)))
  }


}
