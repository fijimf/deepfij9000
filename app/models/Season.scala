
package models

import java.time.LocalDate

import util.DateIterator

case class Season(academicYear: Int, games: List[Game], conferenceMap: List[ConferenceMembership]) {
  def verify(teams: Map[String, Team]): List[String] = {
    val teamSet: Set[String] = conferenceMap.map(_.teamKey).toSet
     List(
     "Academic year is "+academicYear,
     "Number of games is "+games.size,
     "Number of games with results is "+ games.filter(_.result.isDefined).size,
     "Number of teams mapped to conferences is "+conferenceMap.size  ,
     "Number of conferences is "+conferenceMap.map(_.conferenceKey).distinct.size,
     "Teams not mapped to conferences: "+teams.keys.filter(tk=> !teamSet.contains(tk)).mkString(", "),
     "Teams with no games "+ teamSet.filter(tk=>gamesByTeam(tk).isEmpty)




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


