package modules.scraping.util

import modules.scraping.model.GameData
import org.joda.time.LocalDate

object ConferenceTourneySolver {
  def apply(games: List[GameData]): List[(GameData, Boolean)] = {

    val gameCount: Map[(LocalDate, Option[String], String), Int] = games.foldLeft(Map.empty[(LocalDate, Option[String], String), Int])((counts: Map[(LocalDate, Option[String], String), Int], g: GameData) => {
      g.tourneyInfo match {
        case None =>
          g.confInfo.split(" ").toList.filterNot(_.equals("all-conf")) match {
            case conf :: Nil =>
              val key = (g.date, g.location, conf)
              counts + (key -> (counts.getOrElse(key, 0) + 1))
            case _ => counts
          }
        case Some(_) => counts
      }
    })
    val keySet: Set[(LocalDate, Option[String], String)] = gameCount.filter(_._2 > 1).keySet
    val testSet: Map[(String, Option[String]), LocalDate] = keySet.foldLeft(Map.empty[(String, Option[String]), LocalDate])((dates: Map[(String, Option[String]), LocalDate], key: (LocalDate, Option[String], String)) => {
      val nk = (key._3, key._2)
      dates.get(nk) match {
        case Some(d) => if (d.isBefore(key._1)) dates else dates + (nk -> key._1)
        case None => dates + (nk -> key._1)
      }
    })
    games.map(g => {
      g.tourneyInfo match {
        case None =>
          g.confInfo.split(" ").toList.filterNot(_.equals("all-conf")) match {
            case conf :: Nil =>
              testSet.get(conf, g.location) match {
                case Some(d) => g -> (g.date.isBefore(d.plusDays(5)) && g.date.isAfter(d.minusDays(1)))
                case None => g -> false
              }
            case _ => g -> false
          }
        case Some(_) => g -> false
      }
    })
  }
}
