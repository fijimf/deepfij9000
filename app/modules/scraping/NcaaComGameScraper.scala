package modules.scraping

import controllers.model.{GameData, Result, TourneyInfo}
import org.joda.time.LocalDate
import play.api.Logger
import play.api.libs.json._

import scala.util.{Failure, Success, Try}
import scala.xml.Node

trait NcaaComGameScraper {
  val logger: Logger = Logger(this.getClass)

  def getGames(v: JsValue): Try[JsArray] = ((v \ "scoreboard") (0) \ "games").validate[JsArray] match {
    case JsSuccess(value, _) => Success(value)
    case e: JsError =>
      val message: String = "Errors: " + JsError.toJson(e).toString()
      logger.error(message)
      Failure(new IllegalArgumentException(message))
  }

  def getGameData(v: JsValue): Option[GameData] = {
    val optResult = for (
      gs <- (v \ "gameState").asOpt[String] if gs.equalsIgnoreCase("final");
      ps <- (v \ "scoreBreakdown").asOpt[JsArray];
      hs <- (v \ "home" \ "currentScore").asOpt[String];
      as <- (v \ "away" \ "currentScore").asOpt[String]
    ) yield {
      Result(hs.toInt, as.toInt, ps.value.size)
    }

    val optTourneyInfo = for (
      ti <- (v \ "tournament_d").asOpt[String];
      rg <- (v \ "bracket_region").asOpt[String];
      hs <- (v \ "home" \ "team_seed").asOpt[String];
      as <- (v \ "away" \ "team_seed").asOpt[String]
    ) yield {
      TourneyInfo(rg, hs.toInt, as.toInt)
    }

    for (
      sd <- (v \ "startDate").asOpt[String];
      cn <- (v \ "conference").asOpt[String];
      ht <- (v \ "home" \ "name").asOpt[String];
      hk <- pullKeyFromLink(ht);
      at <- (v \ "away" \ "name").asOpt[String];
      ak<- pullKeyFromLink(at)
    ) yield {
      GameData(new LocalDate(sd), hk, ak, optResult, (v \ "location").asOpt[String], optTourneyInfo, cn)
    }
  }

  def pullKeyFromLink(s:String):Option[String] = {
    HTML.loadString(s) match {
      case Success(n:Node) => n.attribute("href").flatMap(_.headOption).map(_.text)
      case Failure(e)=> None
    }
  }

  def stripCallbackWrapper(json: String): String = {
    json
      .replaceFirst( """^callbackWrapper\(\{""", """{""")
      .replaceFirst( """}\);$""", """}""")
      .replaceAll( """,\s+,""", ", ")
  }
}





