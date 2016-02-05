package modules.scraping.requests

import modules.scraping.NcaaComGameScraper
import modules.scraping.model.GameData
import org.joda.time.LocalDate
import play.api.libs.json.JsValue

import scala.util.{Failure, Success}

case class ScoreboardByDate(date:LocalDate) extends JsonScrapeRequest[List[GameData]] with NcaaComGameScraper {
  override def url = "http://data.ncaa.com/jsonp/scoreboard/basketball-men/d1/%04d/%02d/%02d/scoreboard.html".format(date.getYear, date.getMonthOfYear ,date.getDayOfMonth )
  override def preProcessBody(s:String) = stripCallbackWrapper(s)
  override def scrape(js:JsValue):List[GameData] = {
    getGames(js) match {
      case Success(jsa) =>
        jsa.value.toList.flatMap(getGameData)
      case Failure(ex) =>
        logger.error("Failed scraping data ", ex)
        List.empty
    }
  }
}
