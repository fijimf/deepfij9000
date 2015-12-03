package models

import org.joda.time.LocalDate

object Game {
  def fromGameData(gd:GameData, isConfTourn:Boolean, isNeutral:Boolean):Game = {
    Game(gd.date, gd.homeTeamKey, gd.awayTeamKey, gd.result, isNeutral, isConfTourn, gd.tourneyInfo, gd.location )
  }
}

case class Game(date: LocalDate, homeTeamKey: String, awayTeamKey: String, result: Option[Result], isNeutral: Boolean = false, isConfTournament: Boolean, ncaaTourneyInfo:Option[TourneyInfo], location:Option[String]) {
  def opponent(team:String):Option[String] =  team match {
    case `homeTeamKey` => Some(awayTeamKey)
    case `awayTeamKey` => Some(homeTeamKey)
    case _ => None
  }

  def opponent(team:Team):Option[String]  = opponent(team.key)

  def score(team: String): Option[Int] = result.flatMap(res => team match {
    case `homeTeamKey` => Some(res.homeScore)
    case `awayTeamKey` => Some(res.awayScore)
    case _ => None
  })

  def score(team: Team): Option[Int]=score(team.key)

  def isWinner(team: String): Boolean = result.exists(res => (team == homeTeamKey && res.homeScore > res.awayScore) || (team == awayTeamKey && res.awayScore > res.homeScore))
  def isWinner(team: Team): Boolean = isWinner(team.key)

  def isLoser(team: String): Boolean = result.exists(res => (team == homeTeamKey && res.homeScore < res.awayScore) || (team == awayTeamKey && res.awayScore < res.homeScore))
  def isLoser(team: Team): Boolean = isLoser(team.key)

  def atVs(team:String): String ={
    if (isNeutral || team==homeTeamKey){
      "vs."
    } else {
      "@"
    }
  }

  def atVs(team:Team): String=atVs(team.key)
  def wlKey(team:String): String = {
    if(isWinner(team)){
      "W"
    } else if (isLoser(team)) {
      "L"
    } else {
      ""
    }
  }
  def wlKey(team:Team): String=wlKey(team.key)

  def dateStr(fmt:String="MM-dd-yyyy") = {
    date.toString(fmt)
  }
}
