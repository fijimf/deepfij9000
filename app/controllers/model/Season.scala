
 package controllers.model

case class Season(academicYear:Int, games:List[Game], conferenceMap:Map[Team,Conference])
