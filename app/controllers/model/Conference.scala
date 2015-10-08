package controllers.model

case class Conference (
  key:String,
  name:String,
  longName:Option[String] = None,
  logos:Option[LogoUrls] = None,
  social:Option[SocialData] = None
)
