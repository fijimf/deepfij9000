package modules.scraping.requests

import modules.scraping.NcaaOrgTeamScraper
import modules.scraping.model.TeamMap

import scala.xml.Node

case class ShortTeamByYearAndConference(y: Int, c: Int) extends HtmlScrapeRequest[TeamMap] with NcaaOrgTeamScraper {
  override def url = "http://stats.ncaa.org/team/inst_team_list?academic_year=" + y + "&conf_id=" + c + "&division=1&sport_code=MBB"

  override def scrape(n: Node) = TeamMap(extractTeamMap(n))
}
