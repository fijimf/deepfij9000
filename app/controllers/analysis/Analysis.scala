package controllers.analysis


import controllers.model.{Game,  Season, Ncaa}
import org.joda.time.LocalDate

trait Analysis[T] {
  self =>
  def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[T]

  def map[U](fn: T => U): Analysis[U] = {
    new Analysis[U] {
      override def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[U] = (t, d) => {
        self.analyze(ncaa, season).apply(t, d).map(fn)
      }
    }
  }

  def flatMap[U](fn: T => Option[U]): Analysis[U] = {
    new Analysis[U] {
      override def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[U] = (t, d) => {
        self.analyze(ncaa, season).apply(t, d).flatMap(fn)
      }
    }
  }

  def zip[U, V](u: Analysis[U], fn: (T, U) => V): Analysis[V] = {
    new Analysis[V] {
      override def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[V] = (t, d) => {
        for (at <- self.analyze(ncaa, season).apply(t, d);
             au <- u.analyze(ncaa, season).apply(t, d)) yield fn(at, au)
      }
    }
  }
}

class BasicAnalyses {

  object TeamIdentity extends Analysis[String] {
    override def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[String] = (t, d) => Some(t)
  }

  object GameList extends Analysis[List[Game]] {
    override def analyze(ncaa: Ncaa, season: Season): (String, LocalDate) => Option[List[Game]] = (t, d) => {
      Some(season.games.filter(g => (g.homeTeamKey == t || g.awayTeamKey == t) && d.isAfter(g.date)))
    }
  }

  val winList = GameList.zip(TeamIdentity, (games: List[Game], team: String) => games.filter(_.isWinner(team)))
  val lossList = GameList.zip(TeamIdentity, (games: List[Game], team: String) => games.filter(_.isLoser(team)))
  val wp = winList.zip(lossList, (wins: List[Game], losses: List[Game]) => (wins.size, losses.size) match {
    case (0, 0) => None
    case (w, l) => Some((1.0 * w) / (w + l))
  })

  val pointsFor = GameList.zip(TeamIdentity, (games: List[Game], team: String) => games.map(_.score(team)))
  val pointsAgainst = GameList.zip(TeamIdentity, (games: List[Game], team: String) => games.map(g => g.opponent(team).flatMap(opp => g.score(opp))))
}


