
 package controllers.model

case class Season(academicYear:Int, games:List[Game], conferenceMap:List[ConferenceMembership])

 case class ConferenceMembership(teamKey:String, conferenceKey:String)