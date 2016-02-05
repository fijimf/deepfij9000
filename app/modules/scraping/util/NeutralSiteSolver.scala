package modules.scraping.util

import modules.scraping.model.GameData
import play.api.Logger

object NeutralSiteSolver {
  val logger = Logger(this.getClass)
  def apply(gamesWithConfTourn: List[(GameData, Boolean)]): List[(GameData, Boolean, Boolean)] = {
    val locations: List[(String, String)] = gamesWithConfTourn.filter(gd=> gd._1.tourneyInfo.isEmpty && !gd._2).flatMap(g => {
      g._1.location.map(loc => g._1.homeTeamKey -> loc)
    })

    val locLists = locations.foldLeft(Map.empty[String, List[String]])((data: Map[String, List[String]], tup: (String, String)) => data + (tup._1 -> (tup._2 :: data.getOrElse(tup._1, List.empty[String]))))

    val homeLocations: Map[String, Set[String]] = locLists.mapValues(lst => {
      lst.groupBy(s => s).mapValues(_.size).filter(_._2>3).keySet
    })

    gamesWithConfTourn.map(g => {
      (g._1, g._2, g._1.tourneyInfo.isDefined || g._2 || (g._1.location match {
        case Some(loc) =>
          !homeLocations.getOrElse(g._1.homeTeamKey, Set.empty).contains(loc)
        case None =>
          false
      }))
    })
  }
}
