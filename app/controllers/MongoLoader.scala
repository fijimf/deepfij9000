package controllers

import javax.inject.Inject

import controllers.model.{SocialData, LogoUrls, Colors, Team}
import play.api.libs.json.{JsString, JsArray, Json, JsObject}
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{ReadPreference, Cursor}
import reactivemongo.bson.{Macros, BSONDocument, BSONHandler}

import scala.concurrent.Future

class MongoLoader @Inject()(val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents {

  import scala.concurrent.ExecutionContext.Implicits.global
  import play.modules.reactivemongo.json._

  def aliases: JSONCollection = db.collection[JSONCollection]("aliases")

  def teams: JSONCollection = db.collection[JSONCollection]("teams")

  def findByAlias(alias: String) = Action.async {
    val option = aliases
      .find(Json.obj("alias" -> alias))
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()
    option
    option.map { persons =>
      val headOption: Option[JsObject] = persons.headOption
      val orElse: JsObject = headOption.getOrElse(JsObject(Seq("message" -> JsString("Yuck.  Nothing found"))))
      Ok(orElse)
    }
  }

  def saveTeam() = Action.async {

    implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
    implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
    implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
    implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
    val team = Team("georgetown", "Georgetown", "Georgetown University", "Hoyas")
    val fwr: Future[WriteResult] = teams.insert(teamHandler.write(team))
    fwr.map(p => {
      Ok(p.n)
    })
  }
}
