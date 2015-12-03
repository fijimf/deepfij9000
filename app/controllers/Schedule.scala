package controllers

import javax.inject.Inject

import models._
import models.viewdata.TeamPage
import org.joda.time.LocalDate
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}

class Schedule @Inject()(val reactiveMongoApi: ReactiveMongoApi)
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
  implicit val seasonReader: BSONDocumentReader[Season] = Macros.reader[Season]
  implicit val teamReader: BSONDocumentReader[Team] = Macros.reader[Team]

  def team(key: String) = Action.async {
    for (
      s <- loadSeasonFromDb(2016);
      tm <- loadTeamMap
    ) yield {
      s match {
        case Some(season) =>
          tm.get(key) match {
            case Some(team) => 
              val conf: String = season.conferencesByTeam(key)
              val tp: TeamPage = TeamPage(team, season.overallRecord(key), season.confRecord(key), conf, season.gamesByTeam(key),tm, season.conferenceStandings(conf))
              Ok(views.html.teamView(tp))
            case None => Ok("Unknown Team")
          }
        case None => Ok("Failed to load season")
      }
    }
  }

  def loadTeamMap():Future[Map[String, Team]] = {
    val teamCollection = db.collection[BSONCollection]("teams")
    teamCollection.find(BSONDocument()).cursor[Team](ReadPreference.primaryPreferred).collect[List]().map(ll=>ll.map(t=>t.key->t).toMap)
  }
//  def loadTeamFromDb(key: String): Future[Option[Team]] = {
//    val teamCollection = db.collection[BSONCollection]("teams")
//    teamCollection.find(BSONDocument("key" -> key)).cursor[Team](ReadPreference.primaryPreferred).headOption
//  }

  def loadSeasonFromDb(academicYear: Int): Future[Option[Season]] = {
    val seasonCollection = db.collection[BSONCollection]("seasons")
    seasonCollection.find(BSONDocument("academicYear" -> academicYear)).cursor[Season](ReadPreference.primaryPreferred).headOption
  }

  def index() = play.mvc.Results.TODO

  def about() = play.mvc.Results.TODO

  def search(q: String) = play.mvc.Results.TODO

  def date(yyyy: Int, mm: Int, dd: Int) = play.mvc.Results.TODO
}

