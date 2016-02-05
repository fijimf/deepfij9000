package modules.scraping.requests

import modules.scraping.NcaaOrgTeamScraper
import modules.scraping.model.{ConferenceMap, TeamConfMap, TeamMap}

import scala.xml.Node

case class ShortTeamAndConferenceByYear(y: Int) extends HtmlScrapeRequest[TeamConfMap] with NcaaOrgTeamScraper {
  override def url = "http://stats.ncaa.org/team/inst_team_list?academic_year=" + y + "&conf_id=-1&division=1&sport_code=MBB"

  override def scrape(n: Node) = TeamConfMap(ConferenceMap(extractConferenceMap(n)), TeamMap(extractTeamMap(n)))
}
