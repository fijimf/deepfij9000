package modules.scraping.requests

import modules.scraping.NcaaComTeamScraper

import scala.xml.Node

case class ShortNameAndKeyByStatAndPage(s: Int, p: Int) extends HtmlScrapeRequest[Seq[(String, String)]] with NcaaComTeamScraper {
  override def url = "http://www.ncaa.com/stats/basketball-men/d1/current/team/" + s + "/p" + p

  override def scrape(n: Node): Seq[(String, String)] = teamNamesFromStatPage(n)
}
