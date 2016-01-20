package controllers.analysis
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

object SummaryStats {
  def apply[T : Numeric](data: Seq[T]):SummaryStats  = {
    import Numeric.Implicits._
    SummaryStats(new DescriptiveStatistics(data.map(_.toDouble).toArray))
  }

  def apply(ds: DescriptiveStatistics):SummaryStats = {
    SummaryStats(ds.getN,ds.getSum, ds.getSumsq, ds.getMin, ds.getPercentile(0.25), ds.getPercentile(0.5), ds.getPercentile(0.75), ds.getMax,ds.getMean, ds.getVariance, ds.getStandardDeviation, ds.getSkewness, ds.getKurtosis,ds.getQuadraticMean, ds.getGeometricMean)
  }
}
case class SummaryStats(n:Long, sum:Double, sumSq:Double, min:Double, q1:Double, median:Double, q3:Double, max:Double, mean:Double, variance:Double, stdDeviation:Double, skewness:Double, kurtosis:Double, quadraticMean:Double, geometricMean:Double )


