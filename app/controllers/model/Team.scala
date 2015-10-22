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





