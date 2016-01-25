package controllers.dao

import models.{Team, SocialData, LogoUrls, Colors}
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.MultiBulkWriteResult
import reactivemongo.api.{ReadPreference, DefaultDB}
import reactivemongo.bson.{BSONDocumentReader, BSONHandler, BSONDocument, Macros}

import scala.concurrent.Future
import play.modules.reactivemongo.json._

object TeamDao {
  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
  implicit val reader: BSONDocumentReader[Team] = Macros.reader[Team]
}

case class TeamDao(db: DefaultDB) {

  import scala.concurrent.ExecutionContext.Implicits._

  def teams: BSONCollection = db.collection[BSONCollection]("teams")

  def saveTeams(ts: List[Team]): Future[MultiBulkWriteResult] = {
    teams.drop().flatMap(Unit => teams.bulkInsert(ts.map(TeamDao.teamHandler.write).toStream, ordered = false))
  }

  def loadTeamNameMap() = {
    loadAll().map(_.map(t=> t.name-> t.key).toMap)
  }

  def loadAll(): Future[List[Team]] = {
    import TeamDao.reader
    teams.find(BSONDocument()).cursor[Team](ReadPreference.primaryPreferred).collect[List]().mapTo[List[Team]]

  }

}
