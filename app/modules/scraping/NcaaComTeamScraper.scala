package modules.scraping

import controllers.TeamMasterRec

import scala.xml.Node

trait NcaaComTeamScraper {

  def teamNamesFromAlphaPage(node: Node): List[TeamMasterRec] = {
    val schoolList: Option[Node] = (node \\ "div").find(n => attrMatch(n, "id", "school-list")).flatMap(_.headOption)
    extractNamesAndKeys(schoolList).map(t => TeamMasterRec(t._1, t._2)).toList
  }

  def extractNamesAndKeys(schoolList: Option[Node]): Iterator[(String, String)] = {
    for (d <- schoolList.iterator;
         link <- d \\ "a";
         href <- attrValue(link, "href") if href.startsWith("/schools/"))
      yield {
        href.substring(9) -> link.text
      }
  }

  def attrValue(n: Node, attr: String): Option[String] = {
    n.attribute(attr).flatMap(_.headOption).map(_.text)
  }

  def attrMatch(n: Node, attr: String, value: String): Boolean = {
    n.attribute(attr) match {
      case Some(nodeStr) => nodeStr.exists(_.text == value)
      case _ => false
    }
  }

}
