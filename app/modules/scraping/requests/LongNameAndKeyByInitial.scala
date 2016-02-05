package modules.scraping.requests

import modules.scraping.NcaaComTeamScraper
import modules.scraping.model.TeamMaster

import scala.xml.Node

case class LongNameAndKeyByInitial(c: Char) extends HtmlScrapeRequest[TeamMaster] with NcaaComTeamScraper {
  override def url = "http://www.ncaa.com/schools/" + c + "/"

  override def scrape(n: Node) = TeamMaster(teamNamesFromAlphaPage(n))
}
