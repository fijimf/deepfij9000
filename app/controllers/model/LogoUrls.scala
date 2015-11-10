package controllers.model

case class LogoUrls(smallUrl:Option[String]=None, bigUrl:Option[String]=None)

object LogoUrls {
    import reactivemongo.bson._
    implicit val logoUrlsHandler: BSONHandler[BSONDocument, LogoUrls] = Macros.handler[LogoUrls]
}