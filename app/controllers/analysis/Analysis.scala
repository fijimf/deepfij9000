package controllers.analysis

import models.{Game, Season}
import org.joda.time.LocalDate


trait Analysis[T] {
  def teams: Seq[String]

  def dates: Seq[LocalDate]

  def apply(team: String, date: LocalDate): Option[T]

  def apply(team: String): (LocalDate) => Option[T] = apply(team, _: LocalDate)

  def apply(date: LocalDate): (String) => Option[T] = apply(_: String, date)
}


trait Analyzer {
  def apply(season: Season):List[(String, Boolean, Boolean, (String, LocalDate) => Option[Any])]
}

case object WonLostModel extends Analyzer {
  def apply(season: Season):List[(String,Boolean, Boolean, (String, LocalDate) => Option[Any])] = {
    def relevantGames(t: String, d: LocalDate) = season.gamesByTeam(t).filter(!_.date.isAfter(d))
    val wins = (t: String, d: LocalDate) => Some(relevantGames(t, d).count(_.isWinner(t)))
    val losses = (t: String, d: LocalDate) => Some(relevantGames(t, d).count(_.isLoser(t)))
    val wp = (t: String, d: LocalDate) => (wins(t, d), losses(t, d)) match {
      case (Some(w), Some(l)) if w + l > 0 => Some(w / (w + l))
      case _ => None
    }
    val winStreak = (t: String, d: LocalDate) => Some(relevantGames(t, d).reverse.dropWhile(_.result.isEmpty).takeWhile(_.isWinner(t)))
    val lossStreak = (t: String, d: LocalDate) => Some(relevantGames(t, d).reverse.dropWhile(_.result.isEmpty).takeWhile(_.isLoser(t)))
    List(
      ("wins", true, true, wins),
      ("losses" , false, true, losses),
      ("wp", true, true,  wp),
      ("winStreak" , true, true,  winStreak),
      ("lossStreak", false, true,  lossStreak)
    )
  }
}

case object ScoringModel extends Analyzer {
  def apply(season: Season):List[(String, Boolean, Boolean, (String, LocalDate) => Option[Any])] = {
    def relevantGames(t: String, d: LocalDate) = season.gamesByTeam(t).filter(!_.date.isAfter(d))
    val score = (t: String, d: LocalDate) => {
      Some(SummaryStats(relevantGames(t, d).flatMap(g => g.score(t))))
    }
    val oppScore = (t: String, d: LocalDate) => {
      Some(SummaryStats(relevantGames(t, d).flatMap(g => g.opponent(t).flatMap(opp => g.score(opp)))))
    }
    val margin = (t: String, d: LocalDate) => {
      Some(SummaryStats(relevantGames(t, d).flatMap(g => g.opponent(t).flatMap(opp => g.score(opp).flatMap(os => g.score(t).map(_ - os))))))
    }

    val gs = List(
      "mean" -> { ss: SummaryStats => ss.mean },
      "sum" -> { ss: SummaryStats => ss.sum },
      "stdDev" -> { ss: SummaryStats => ss.stdDeviation },
      "min" -> { ss: SummaryStats => ss.min },
      "q1" -> { ss: SummaryStats => ss.q1 },
      "med" -> { ss: SummaryStats => ss.median },
      "q3" -> { ss: SummaryStats => ss.q3 },
      "max" -> { ss: SummaryStats => ss.max },
      "skew" -> { ss: SummaryStats => ss.skewness },
      "kurt" -> { ss: SummaryStats => ss.kurtosis }
    )
    val fs: List[(String, Boolean, (String, LocalDate) => Option[SummaryStats])] = List(
      ("Score", true, score(_: String, _: LocalDate)),
      ("OppScore", false, oppScore(_: String, _: LocalDate)),
      ("Margin", true, margin(_: String, _: LocalDate))
    )

    for (f <- fs; g <- gs) yield (g._1 + f._1, f._2, true, extract(f._3, g._2))
  }

  def extract[K: Numeric](f: (String, LocalDate) => Option[SummaryStats], g: (SummaryStats) => K): (String, LocalDate) => Option[Double] = {
    (t: String, d: LocalDate) => {
      import Numeric.Implicits._
      val optK: Option[K] = f(t, d).map(ss => g(ss))
      optK.map(_.toDouble)
    }
  }
}

