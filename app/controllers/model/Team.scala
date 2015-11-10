package controllers.model

case class Team(
  key:String, 
  name:String, 
  longName:String,
  nickname:String,
  logos:Option[LogoUrls] = None, 
  colors:Option[Colors] = None,
  socialMedia:Option[SocialData] = None
)

object Team {
  import reactivemongo.bson._
  implicit val teamHandler: BSONHandler[BSONDocument, Team] = Macros.handler[Team]
  implicit val colorsHandler: BSONHandler[BSONDocument, Colors] = Macros.handler[Colors]
  implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
}





