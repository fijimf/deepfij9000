package controllers.dao

import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{ReadPreference, DefaultDB}
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future
import play.modules.reactivemongo.json._

case class AliasDao(db: DefaultDB) {

  import scala.concurrent.ExecutionContext.Implicits._

  def aliases: BSONCollection = db.collection[BSONCollection]("aliases")

  def loadAliasMap(): Future[Map[String, String]] = {
    aliases.find(BSONDocument()).cursor[BSONDocument](ReadPreference.primaryPreferred).collect[List]().map(list => {
      list.flatMap(bso => {
        for (
          a <- bso.getAs[String]("alias");
          k <- bso.getAs[String]("key")
        ) yield a -> k
      }).toMap
    })
  }

}
