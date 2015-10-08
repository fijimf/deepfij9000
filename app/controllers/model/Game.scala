package controllers.model

import org.joda.time.LocalDateTime

case class Game(date: LocalDateTime, homeTeam: Team, awayTeam: Team, result: Option[Result], isNeutral: Boolean = false, isConfTournament: Option[Boolean]) {
  def opponent(team:Team) =  team match {
    case `homeTeam` => Some(awayTeam)
    case `awayTeam` => Some(homeTeam)
    case _ => None
  }

  def score(team: Team): Option[Int] = result.flatMap(res => team match {
    case `homeTeam` => Some(res.homeScore)
    case `awayTeam` => Some(res.awayScore)
    case _ => None
  })

  def isWinner(team: Team): Boolean = result.exists(res => (team == homeTeam && res.homeScore > res.awayScore) || (team == awayTeam && res.awayScore > res.homeScore))

  def isLoser(team: Team): Boolean = result.exists(res => (team == homeTeam && res.homeScore < res.awayScore) || (team == awayTeam && res.awayScore < res.homeScore))
}
