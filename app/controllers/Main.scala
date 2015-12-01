package controllers

import javax.inject.Inject

import controllers.model._
import org.joda.time.LocalDate
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}

class Main @Inject()(val reactiveMongoApi: ReactiveMongoApi)
                    (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, LocalDate] {
    def read(time: BSONDateTime) = new LocalDate(time.value)

    def write(jdtime: LocalDate) = BSONDateTime(jdtime.toDate.getTime)
  }

  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
  implicit val resultHandler: BSONHandler[BSONDocument, Result] = Macros.handler[Result]
  implicit val tourneyInfoHandler: BSONHandler[BSONDocument, TourneyInfo] = Macros.handler[TourneyInfo]
  implicit val gameHandler: BSONHandler[BSONDocument, Game] = Macros.handler[Game]
  implicit val conferenceMembershipHandler: BSONHandler[BSONDocument, ConferenceMembership] = Macros.handler[ConferenceMembership]
  implicit val seasonHandler: BSONHandler[BSONDocument, Season] = Macros.handler[Season]
  implicit val seasokkkkkkkkknHandler: BSONDocumentReader[Season] = Macros.reader[Season]

  implicit val reader: BSONDocumentReader[Team] = Macros.reader[Team]

  def team(key: String) = Action.async {
    for (
      s <- loadSeasonFromDb(2016);
      t <- loadTeamFromDb(key)
    ) yield {
      s match {
        case Some(season) =>
          t match {
            case Some(team) => Ok(views.html.teamView(team))
            case None => Ok("Unknown Team")
          }
        case None => Ok("Failed to load season")
      }
    }
  }

  def loadTeamFromDb(key: String): Future[Option[Team]] = {
    val teamCollection = db.collection[BSONCollection]("teams")
    teamCollection.find(BSONDocument("key" -> key)).cursor[Team](ReadPreference.primaryPreferred).headOption
  }

  def loadSeasonFromDb(academicYear: Int): Future[Option[Season]] = {
    val seasonCollection = db.collection[BSONCollection]("seasons")
    seasonCollection.find(BSONDocument("academicYear" -> academicYear)).cursor[Season](ReadPreference.primaryPreferred).headOption
  }

}
