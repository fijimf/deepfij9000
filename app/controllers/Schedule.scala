package controllers

import javax.inject.Inject

import models._
import models.viewdata.TeamPage
import org.joda.time.LocalDate
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{Lang, Messages, I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}

class Schedule @Inject()(val reactiveMongoApi: ReactiveMongoApi, val messagesApi: MessagesApi)
                    (implicit ec: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents with I18nSupport{

  import play.api.i18n.Messages.Implicits._
  import play.api.Play.current
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

  def editTeam(key:String) = Action.async {implicit request =>
    val form = Form(
      mapping(
        "key"->text,
        "name"->text,
        "longName"->text,
        "nickname"->text,
        "logos"-> optional(
          mapping(
             "smallUrl"->optional(text),
             "bigUrl"->optional(text)
          )(LogoUrls.apply)(LogoUrls.unapply)
        ),
        "colors"-> optional(
          mapping(
            "primary"->optional(text),
            "secondary"->optional(text)
          )(Colors.apply)(Colors.unapply)
        ),
        "socialMedia"->  optional(
        mapping(
          "url"->optional(text),
          "twitter"->optional(text),
          "instagram"->optional(text),
          "facebook"->optional(text)
        )(SocialData.apply)(SocialData.unapply)
        )
      )(Team.apply)(Team.unapply)
    )
    for (
      tm <- loadTeamFromDb(key)
    ) yield {
      tm match {
        case Some(t) =>
          form.fill(t)
          Ok(views.html.teamEdit(form.fill(t), t.name))
        case None => Ok(views.html.resourceNotFound("Team", key))
      }
    }
  }

  def saveTeam = play.mvc.Results.TODO

  def loadTeamMap():Future[Map[String, Team]] = {
    val teamCollection = db.collection[BSONCollection]("teams")
    teamCollection.find(BSONDocument()).cursor[Team](ReadPreference.primaryPreferred).collect[List]().map(ll=>ll.map(t=>t.key->t).toMap)
  }
  def loadTeamFromDb(key: String): Future[Option[Team]] = {
    val teamCollection = db.collection[BSONCollection]("teams")
    teamCollection.find(BSONDocument("key" -> key)).cursor[Team](ReadPreference.primaryPreferred).headOption
  }

  def loadSeasonFromDb(academicYear: Int): Future[Option[Season]] = {
    val seasonCollection = db.collection[BSONCollection]("seasons")
    seasonCollection.find(BSONDocument("academicYear" -> academicYear)).cursor[Season](ReadPreference.primaryPreferred).headOption
  }

  def index() = play.mvc.Results.TODO

  def about() = play.mvc.Results.TODO

  def search(q: String) = play.mvc.Results.TODO

  def date(yyyy: Int, mm: Int, dd: Int) = play.mvc.Results.TODO


}

