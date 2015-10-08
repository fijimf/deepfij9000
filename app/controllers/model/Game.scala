package controllers.model
import org.joda.time.DateTime

case class Game(date:DateTime, homeTeam:Team, awayTeam:Team, isNeutral:Boolean = false, isConfTournament:Option[Boolean])
