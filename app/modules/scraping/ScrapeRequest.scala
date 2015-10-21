package modules.scraping

import controllers.{TeamMaster, ConferenceMap, TeamMap, TeamConfMap}

import scala.xml.Node

sealed trait ScrapeRequest[T] {
  def url: String

  def scrape(n: Node): T
}

case class ShortTeamAndConferenceByYear(y: Int) extends ScrapeRequest[TeamConfMap] with NcaaOrgTeamScraper {
  override def url = "http://stats.ncaa.org/team/inst_team_list?academic_year=" + y + "&conf_id=-1&division=1&sport_code=MBB"

  override def scrape(n: Node) = TeamConfMap(ConferenceMap(extractConferenceMap(n)), TeamMap(extractTeamMap(n)))
}

case class ShortTeamByYearAndConference(y: Int, c: Int) extends ScrapeRequest[TeamMap] with NcaaOrgTeamScraper {
  override def url = "http://stats.ncaa.org/team/inst_team_list?academic_year=" + y + "&conf_id=" + c + "&division=1&sport_code=MBB"

  override def scrape(n: Node) = TeamMap(extractTeamMap(n))
}

case class LongNameAndKeyByInitial(c: Char) extends ScrapeRequest[TeamMaster] with NcaaComTeamScraper {
  override def url: String = "http://www.ncaa.com/schools/" + c + "/"

  override def scrape(n: Node) = TeamMaster(teamNamesFromAlphaPage(n))
}

//case class ShortNameAndKeyByStatAndPage(s: Int, p: Int) extends ScrapeRequest
//
//case class TeamDetail(key: String) extends ScrapeRequest