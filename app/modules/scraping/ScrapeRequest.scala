package modules.scraping

import controllers.model._
import controllers.{TeamMaster, ConferenceMap, TeamMap, TeamConfMap}
import org.joda.time.LocalDate
import play.api.libs.json.JsValue

import scala.xml.Node

sealed trait ScrapeRequest[T] {
  def url: String

  def scrape(n: Node): T
}

sealed trait JsonScrapeRequest[T] {
  def url: String

  def preProcessBody(s:String):String = s

  def scrape(js:JsValue): T
}

case class Scoreboard(date:String, games:List[Game])


case class ScoreboardByDate(date:LocalDate) extends JsonScrapeRequest[Scoreboard] with NcaaComGameScraper {
  override def url = "http://data.ncaa.com/jsonp/scoreboard/basketball-men/d1/" + date.getYear + "/" + date.getMonthOfYear + "/" + date.getDayOfMonth + "/scoreboard.html"
  override def preProcessBody(s:String) = stripCallbackWrapper(s)
  override def scrape(js:JsValue):Scoreboard = {


    Scoreboard("",List.empty)
  }
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
  override def url = "http://www.ncaa.com/schools/" + c + "/"

  override def scrape(n: Node) = TeamMaster(teamNamesFromAlphaPage(n))
}

case class ShortNameAndKeyByStatAndPage(s: Int, p: Int) extends ScrapeRequest[Seq[(String, String)]] with NcaaComTeamScraper {
  override def url = "http://www.ncaa.com/stats/basketball-men/d1/current/team/" + s + "/p" + p

  override def scrape(n: Node): Seq[(String, String)] = teamNamesFromStatPage(n)
}

case class TeamDetail(key: String, shortName:String) extends ScrapeRequest[Team] with NcaaComTeamScraper {
  override def url = "http://www.ncaa.com/schools/" + key+"/"

  override def scrape(n: Node) = {
    val longName = schoolName(n).getOrElse(shortName)
    val metaInfo = schoolMetaInfo(n)
    val nickname = metaInfo.getOrElse("nickname", "MISSING")
    val primaryColor = schoolPrimaryColor(n)
    val secondaryColor = primaryColor.map(c => desaturate(c, 0.4))
    val logoUrl = schoolLogo(n)
    val officialUrl = schoolOfficialWebsite(n)
    val officialTwitter = schoolOfficialTwitter(n)
    val conference = metaInfo.getOrElse("conf", "MISSING")
    Team(key, shortName, longName, nickname,Some(LogoUrls(logoUrl, logoUrl.map(_.replace("40","70")))), Some(Colors(primaryColor,secondaryColor)  ), Some(SocialData(officialUrl,officialTwitter, None,None)))

  }
}
