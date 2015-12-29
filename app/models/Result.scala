package models

case class Result(homeScore: Int, awayScore: Int, periods: Int) {
  def margin = Math.abs(homeScore - awayScore)
}
