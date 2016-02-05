package modules.scraping.model

case class ConferenceMap(data: Map[Int, String] = Map.empty) {
  def merge(cm: ConferenceMap): ConferenceMap = {
    ConferenceMap(data ++ cm.data)
  }
}
