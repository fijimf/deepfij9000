package controllers.model

import controllers.model.{SocialData, Colors, LogoUrls}

case class Team(
  key:String, 
  name:String, 
  longName:Option[String] = None, 
  nickname:Option[String] = None, 
  logos:Option[LogoUrls] = None, 
  colors:Option[Colors] = None,
  socialMedia:Option[SocialData] = None
)





