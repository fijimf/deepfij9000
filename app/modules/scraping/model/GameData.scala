package modules.scraping.model

import models.{Result, TourneyInfo}
import org.joda.time.LocalDate

case class GameData(date: LocalDate, homeTeamKey: String, awayTeamKey: String, result: Option[Result], location: Option[String], tourneyInfo: Option[TourneyInfo], confInfo: String)
