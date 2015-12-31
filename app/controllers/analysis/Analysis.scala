package controllers.analysis

import models.{Season, Ncaa, Game}
import org.joda.time.LocalDate


trait Analysis[T] {
  def teams: Seq[String]

  def dates: Seq[LocalDate]

  def apply(team: String, date: LocalDate): Option[T]

  def apply(team: String) = apply(team, _)

  def apply(date: LocalDate) = apply(_, date)
}



trait Analyzer[T] {
  def apply(season: Season): Analysis[T]
}

object Wins extends Analyzer[Int] {
  override def apply(season: Season): Analysis[Int] = new Analysis[Int] {
    override def teams: Seq[String] = season.allTeams

    override def dates: Seq[LocalDate] = season.allDates
    override def apply(team: String, date: LocalDate): Option[Int] = {
      val games: List[Game] = season.gamesByTeam(team)
      Some(games.filter(!_.date.isAfter(date)).count(_.isWinner(team)))
    }
  }
}

case object WonLostModel {
  def apply(season:Season) = {
    def relevantGames(t: String, d: LocalDate) = season.gamesByTeam(t).filter(!_.date.isAfter(d))
    val wins = (t: String, d: LocalDate) => Some(relevantGames(t,d).count(_.isWinner(t)))
    val losses = (t: String, d: LocalDate) => Some(relevantGames(t,d).count(_.isLoser(t)))
    val wp = (t: String, d: LocalDate) => (wins(t,d), losses(t,d)) match {
      case (Some(w), Some(l)) if w + l > 0 => Some(w/(w+l))
      case _ => None
    }
    val winStreak = (t: String, d: LocalDate) => Some(relevantGames(t,d).reverse.dropWhile(_.result.isEmpty).takeWhile(_.isWinner(t)))
    val lossStreak = (t: String, d: LocalDate) => Some(relevantGames(t,d).reverse.dropWhile(_.result.isEmpty).takeWhile(_.isLoser(t)))
    List(wins, losses,wp, winStreak, lossStreak)
  }
}

case object ScoringModel {
  def apply(season:Season) = {
    def relevantGames(t: String, d: LocalDate) = season.gamesByTeam(t).filter(!_.date.isAfter(d))
    val oppScore = (t: String, d: LocalDate) => {
      val map: List[Int] = relevantGames(t, d).flatMap(g => g.opponent(t).flatMap(opp => g.score(opp)))
      Some(DescriptiveStatistics(map))
    }
    val losses = (t: String, d: LocalDate) => Some(relevantGames(t,d).count(.isLoser(t)))
    val wp = (t: String, d: LocalDate) => (wins(t,d), losses(t,d)) match {
      case (Some(w), Some(l)) if w + l > 0 => Some(w/(w+l))
      case _ => None
    }
    val winStreak = (t: String, d: LocalDate) => Some(relevantGames(t,d).reverse.dropWhile(.result.isEmpty).takeWhile(.isWinner(t)))
    val lossStreak = (t: String, d: LocalDate) => Some(relevantGames(t,d).reverse.dropWhile(.result.isEmpty).takeWhile(.isLoser(t)))
    List(wins, losses,wp, winStreak, lossStreak)
  }

}

