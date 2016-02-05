package modules.scraping.model

case class TeamConfMap(cm: ConferenceMap = ConferenceMap(), tm: TeamMap = TeamMap()) {
  def merge(tcm: TeamConfMap): TeamConfMap = {
    TeamConfMap(cm.merge(tcm.cm), tm.merge(tcm.tm))
  }
}
