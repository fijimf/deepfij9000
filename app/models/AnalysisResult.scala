package models

import org.joda.time.LocalDate
import play.api.Logger

case class ModelResult(name: String, isHigherBetter: Boolean, isNumeric: Boolean, series: List[ModelPop])

case class ModelPop(date: LocalDate, data: List[ModelValue])

case class ModelValue(team: String, strValue: Option[String], dblValue: Option[Double])

object ModelValue {
  val logger = Logger(ModelValue.getClass)
  def apply(team: String, value: String): ModelValue = {
    ModelValue(team, Some(value), None)
  }

  def apply(team: String, value: Double): ModelValue = {
    ModelValue(team, None, Some(value))
  }
}
