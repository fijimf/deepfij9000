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

