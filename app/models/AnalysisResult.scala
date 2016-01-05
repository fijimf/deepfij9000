package models

import org.joda.time.LocalDate

case class ModelResult(name:String, series:List[ModelPop])

case class ModelPop(date: LocalDate, data:List[ModelValue])

case class ModelValue(team:String, value:Any)
