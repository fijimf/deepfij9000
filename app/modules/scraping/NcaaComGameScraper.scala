package modules.scraping

import play.api.libs.json.JsValue


trait NcaaComGameScraper {



  def ripJson[T](json: String, f: (JsValue => T)): List[T] = {
    if (StringUtils.isBlank(json)) {
      List.empty
    } else {
      try {
        val parse: JsValue = Json.parse(stripCallbackWrapper(json))
        val games = ((parse \ "scoreboard")(0) \ "games").validate[JsArray] match {
          case JsSuccess(g, _) => g.asInstanceOf[JsArray].value.toList
          case _ => {
            logger.error("Error parsing Json")
            List.empty
          }
        }
        games.map(f)
      } catch {
        case t: Throwable => {
          logger.error("Error parsing Json", t)
          // logger.error(json)
          List.empty
        }
      }
    }
  }

  def ripGames(json: String, date: LocalDate): List[ResultData] = {
    ripJson(json, ripGameResult).flatten.map(tup => ResultData(GameData(date, tup._1, tup._2), tup._3))
  }

  val ripTwitters: ((String, JsValue) => Option[(String, String)]) = {
    case (ha, j) =>
      val t = (j \ ha \ "nameSeo").asOpt[String]
      val bb = (j \ ha \ "social" \ "twitter" \ "accounts" \ "sport").asOpt[String]
      val ad = (j \ ha \ "social" \ "twitter" \ "accounts" \ "athleticDept").asOpt[String]
      val cf = (j \ ha \ "social" \ "twitter" \ "accounts" \ "conference").asOpt[String]
      (t, bb.orElse(ad).orElse(cf)) match {
        case (Some(team), Some(twitter)) => Some(team -> twitter)
        case _ => None
      }
  }

  def ripTwitterMap(json: String): Map[String, String] = {
    (ripJson(json, ripTwitters("home", _)).flatten ++ ripJson(json, ripTwitters("away", _)).flatten).toMap
  }

  def ripColors(json: String): Map[String, String] = {
    Map.empty[String, String]
  }

  val ripGameResult: (JsValue => Option[(String, String, Option[(Int, Int)])]) = {
    j =>
      val result = (
        teamData(j, "home", "currentScore").flatMap(x => catching(classOf[NumberFormatException]) opt x.toInt),
        teamData(j, "away", "currentScore").flatMap(x => catching(classOf[NumberFormatException]) opt x.toInt)
        ) match {
        case (Some(h), Some(a)) => Some(h.asInstanceOf[Int], a.asInstanceOf[Int])
        case _ => None
      }

      val teams = (teamData(j, "home", "nameSeo"), teamData(j, "away", "nameSeo")) match {
        case (Some(h), Some(a)) => Some(h.toString, a.toString, result)
        case _ => None
      }
      teams
  }

  def teamData(game: JsValue, homeOrAway: String, item: String): Option[String] = {
    (game \ homeOrAway \ item).asOpt[String]
  }

  def stripCallbackWrapper(json: String): String = {
    json
      .replaceFirst( """^callbackWrapper\(\{""", """{""")
      .replaceFirst( """}\);$""", """}""")
      .replaceAll( """,\s+,""", ", ")
  }
}
