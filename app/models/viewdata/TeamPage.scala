package models.viewdata

import models.{Game, Team}

case class TeamPage(team:Team, record:(Int,Int), confRecord:(Int, Int), conference:String, randomQuote:String, schedule:List[Game], teamMap:Map[String,Team], standings:List[(String, (Int, Int), (Int, Int))],specRecords:List[(String, Int, Int,Option[Double])])
