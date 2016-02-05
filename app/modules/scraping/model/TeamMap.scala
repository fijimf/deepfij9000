package modules.scraping.model

case class TeamMap(data: Map[Int, String] = Map.empty) {
  def merge(tm: TeamMap): TeamMap = {
    TeamMap(data ++ tm.data)
  }
}
