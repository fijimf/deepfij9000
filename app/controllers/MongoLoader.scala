package controllers

import javax.inject.Inject

import play.api.libs.json.{JsString, JsArray, Json, JsObject}
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.{ReadPreference, Cursor}

import scala.concurrent.Future

class MongoLoader @Inject()(val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents {
  import scala.concurrent.ExecutionContext.Implicits.global
  import play.modules.reactivemongo.json._

  def collection: JSONCollection = db.collection[JSONCollection]("aliases")

  def findByAlias(alias: String) = Action.async {
    val option = collection
      .find(Json.obj("alias" -> alias))
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List]()
    option
    option.map { persons =>
      val headOption: Option[JsObject] = persons.headOption
      val orElse: JsObject = headOption.getOrElse(JsObject(Seq("message"->JsString("Yuck.  Nothing found"))))
      Ok(orElse)
    }
  }
}
