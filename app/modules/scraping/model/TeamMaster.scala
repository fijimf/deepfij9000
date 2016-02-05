package modules.scraping.model

case class TeamMaster(teams:List[TeamMasterRec]=List.empty[TeamMasterRec]) {
  val byKey = teams.map(r=>r.key-> r.name).toMap
  val byName = teams.map(r=>r.name-> r.key).toMap
  def merge(tm:TeamMaster):TeamMaster = {
    TeamMaster(teams++tm.teams)
  }
}
