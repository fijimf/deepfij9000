package controllers.analysis
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

case class SummaryStats(data:Seq[Numeric[_]]) {
  private val ds:DescriptiveStatistics = new DescriptiveStatistics(data.map(_.toDouble())).

}
