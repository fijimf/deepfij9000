package controllers.model

import org.joda.time.LocalDateTime

object Game {
  def fromGameData(gd:GameData):Game = {
    
  }
}

case class Game(date: LocalDate, homeTeamKey: String, awayTeamKey: String, result: Option[Result], isNeutral: Boolean = false, isConfTournament: Option[Boolean], ncaaTourneyInfo:Option[TourneyInfo], location:Option[String], periods:Int) {
  def opponent(team:String) =  team match {
    case `homeTeamKey` => Some(awayTeamKey)
    case `awayTeamKey` => Some(homeTeamKey)
    case _ => None
  }

  def score(team: Team): Option[Int] = result.flatMap(res => team match {
    case `homeTeamKey` => Some(res.homeScore)
    case `awayTeamKey` => Some(res.awayScore)
    case _ => None
  })

  def isWinner(team: String): Boolean = result.exists(res => (team == homeTeamKey && res.homeScore > res.awayScore) || (team == awayTeamKey && res.awayScore > res.homeScore))

  def isLoser(team: String): Boolean = result.exists(res => (team == homeTeamKey && res.homeScore < res.awayScore) || (team == awayTeamKey && res.awayScore < res.homeScore))
}
