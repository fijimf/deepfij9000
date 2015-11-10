package controllers.model

case class SocialData(url:Option[String]=None, twitter:Option[String]=None,instagram:Option[String]=None, facebook:Option[String]=None)

object SocialData {
  import reactivemongo.bson._
  implicit val socialDataHandler: BSONHandler[BSONDocument, SocialData] = Macros.handler[SocialData]
}