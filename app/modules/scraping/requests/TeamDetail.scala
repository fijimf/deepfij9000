package modules.scraping.requests

import models.{Colors, LogoUrls, SocialData, Team}
import modules.scraping.NcaaComTeamScraper

import scala.xml.Node

case class TeamDetail(key: String, shortName:String) extends HtmlScrapeRequest[Team] with NcaaComTeamScraper {
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
    val officialFacebook = schoolOfficialFacebook(n)
    val conference = metaInfo.getOrElse("conf", "MISSING")
    Team(key, shortName, longName, nickname,Some(LogoUrls(logoUrl, logoUrl.map(_.replace("40","70")))), Some(Colors(primaryColor,secondaryColor)  ), Some(SocialData(officialUrl,officialTwitter, None,officialFacebook)))

  }
}
