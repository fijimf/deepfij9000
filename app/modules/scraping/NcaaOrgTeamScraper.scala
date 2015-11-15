package modules.scraping

import scala.xml.Node

trait NcaaOrgTeamScraper {
  def extractConferenceMap(htmlNode: Node): Map[Int, String] = {
    (htmlNode \\ "li" \ "a").filter(_.attribute("href").flatMap(_.headOption).exists(_.text.startsWith("javascript:changeConference"))).map(n => {
      val id = n.attribute("href").flatMap(_.headOption).map(_.text.replace("javascript:changeConference(", "").replace(");", "").toDouble.toInt)
      id -> n.text
    }).filter(_._1.isDefined).map((tup: (Option[Int], String)) => (tup._1.get, tup._2)).toMap.filterNot(_._2=="ALL TEAMS")

  }

  def extractTeamMap(htmlNode: Node): Map[Int, String] = {
    (htmlNode \\ "td" \ "a").map(n => {
      val id = n.attribute("href").flatMap(_.headOption).map(_.text.replaceAll( """/team/index/\d+\?org_id=""", "").toInt)
      id -> n.text
    }).filter(_._1.isDefined).map(tup => tup._1.get -> tup._2).toMap
  }


}
