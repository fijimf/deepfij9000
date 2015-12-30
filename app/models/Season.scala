
package models

import org.joda.time.LocalDate

import util.DateIterator

case class Season(academicYear: Int, games: List[Game], conferenceMap: List[ConferenceMembership]) {
  def verify(teams: Map[String, Team]): List[String] = {
    val teamSet: Set[String] = conferenceMap.map(_.teamKey).toSet
    List(
      "Academic year is " + academicYear,
      "Number of games is " + games.size,
      "Number of games with results is " + games.count(_.result.isDefined),
      "Number of teams mapped to conferences is " + conferenceMap.size,
      "Number of conferences is " + conferenceMap.map(_.conferenceKey).distinct.size,
      "Teams not mapped to conferences: " + teams.keys.filter(tk => !teamSet.contains(tk)).mkString(", "),
      "Teams with no games " + teamSet.filter(tk => gamesByTeam(tk).isEmpty)


    )
  }

  val conferences = conferenceMap.groupBy(_.conferenceKey).mapValues(_.map(_.teamKey))
  val conferencesByTeam = conferenceMap.map(c => c.teamKey -> c.conferenceKey).toMap
  val allTeams = (games.map(_.homeTeamKey) ++ games.map(_.awayTeamKey)).distinct.sorted
  val allDates = {
    games.map(_.date) match {
      case Nil => List.empty[LocalDate]
      case ds => DateIterator(ds.minBy(_.toDate.getTime), ds.maxBy(_.toDate.getTime)).toList
    }
  }
  private[this] val homeGames = games.groupBy(_.homeTeamKey)
  private[this] val awayGames = games.groupBy(_.awayTeamKey)

  def gamesByTeam(t: String): List[Game] = (for (
    hg <- homeGames.get(t);
    ag <- awayGames.get(t))
    yield {
      (hg ++ ag).sortBy(_.date.toDate.getTime)
    }).getOrElse(List.empty[Game])

  val gamesByDate: (LocalDate => List[Game]) = games.groupBy(_.date)

  def calcRecord(t: String, list: List[Game]): (Int, Int) = {
    (list.count(_.isWinner(t)), list.count(_.isLoser(t)))
  }

  def calcRecord(label: String, selector: List[Game] => List[Game], t: String, list: List[Game]): (String, Int, Int, Option[Double]) = {
    val record = calcRecord(t, selector(list))
    record match {
      case (0, 0) => (label, 0, 0, None)
      case (w, l) => (label, w, l, Some(w.toDouble / (w + l).toDouble))
    }
  }

  def overallRecord(t: String): (Int, Int) = {
    calcRecord(t, gamesByTeam(t))
  }

  def isConferenceGame(g: Game): Boolean = {
    (conferencesByTeam.get(g.homeTeamKey), conferencesByTeam.get(g.awayTeamKey)) match {
      case (None, None) => false
      case (a, b) => a == b
    }
  }

  implicit val recordOrdering: Ordering[(String, (Int, Int), (Int, Int))] = new Ordering[(String, (Int, Int), (Int, Int))] {
    def compare(x: (String, (Int, Int), (Int, Int)), y: (String, (Int, Int), (Int, Int))) = {
      val a = compareRecord(x._2, y._2)
      if (a == 0) {
        val b = compareRecord(x._3, y._3)
        if (b == 0) {
          x._1.compareTo(y._1)
        } else {
          b
        }
      } else {
        a
      }
    }
  }

  def conferenceStandings(c: String): List[(String, (Int, Int), (Int, Int))] = {
    conferences.get(c).map(ts => {
      ts.map(t => (t, confRecord(t), overallRecord(t))).sorted
    }).getOrElse(List.empty[(String, (Int, Int), (Int, Int))])
  }

  def specRecords(t: String): List[(String, Int, Int, Option[Double])] = {
    val gs: List[Game] = gamesByTeam(t).filter(_.result.isDefined)
    List(
      calcRecord("Overall", (g) => g, t, gs),
      calcRecord("Conference", _.filter(g => isConferenceGame(g)), t, gs),
      calcRecord("Non-conference", _.filter(g => !isConferenceGame(g)), t, gs),
      calcRecord("Home", _.filter(g => g.homeTeamKey == t && !g.isNeutral), t, gs),
      calcRecord("Away", _.filter(g => g.awayTeamKey == t && !g.isNeutral), t, gs),
      calcRecord("Neutral", _.filter(g => g.isNeutral), t, gs),
      calcRecord("Overtime", _.filter(g => g.result.get.periods > 2), t, gs),
      calcRecord("Margin < 5", _.filter(g => g.result.get.margin < 5), t, gs),
      calcRecord("Margin > 15", _.filter(g => g.result.get.margin > 15), t, gs),
      calcRecord("Scoring < 60 ", _.filter(g => g.score(t).get <= 60), t, gs),
      calcRecord("Scoring > 60 ", _.filter(g => g.score(t).get > 60), t, gs),
      calcRecord("Scoring < 80", _.filter(g => g.score(t).get <= 80), t, gs),
      calcRecord("Scoring > 80", _.filter(g => g.score(t).get > 80), t, gs),
      calcRecord("Scoring < 100", _.filter(g => g.score(t).get <= 100), t, gs),
      calcRecord("Scoring > 100", _.filter(g => g.score(t).get > 100), t, gs),
      calcRecord("Last 5", _.filter(g => g.result.isDefined).takeRight(5), t, gs),
      calcRecord("Last 10", _.filter(g => g.result.isDefined).takeRight(10), t, gs),
      calcRecord("November", _.filter(g => g.date.getMonthOfYear == 11), t, gs),
      calcRecord("December", _.filter(g => g.date.getMonthOfYear == 12), t, gs),
      calcRecord("January", _.filter(g => g.date.getMonthOfYear == 1), t, gs),
      calcRecord("February", _.filter(g => g.date.getMonthOfYear == 2), t, gs),
      calcRecord("March", _.filter(g => g.date.getMonthOfYear == 3), t, gs)

    )
  }


  private[this] def compareRecord(i: (Int, Int), j: (Int, Int)): Int = {
    if (i._1 - i._2 == j._1 - j._2) {
      j._1 - i._1
    } else {
      (j._1 - j._2) - (i._1 - i._2)
    }
  }


  def confRecord(t: String): (Int, Int) = {
    calcRecord(t, gamesByTeam(t).filter(g => isConferenceGame(g)))
  }


}


