package controllers.module

case class Team(
  key:String, 
  name:String, 
  longName:Option[String] = None, 
  nickname:Option[String] = None, 
  logos:Option[TeamLogos] = None, 
  colors:Option[TeamColors] = None,
  socialMedia:Option[TeamSocial] = None
)

case class TeamLogos(smallUrl:Option[String]=None, bigUrl:Option[String]=None)

case class TeamColors(primary:Option[String]=None, secondary:Option[String]=None)

case class TeamSocial(url:Option[String]=None, twitter:Option[String]=None,instagram:Option[String]=None, facebook:Option[String]=None)
