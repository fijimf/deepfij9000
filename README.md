Deep Fij 9000
=============

This is the latest iteration of my college basketball analysis website, implemented with the Play Framework.

Basicall scrapes the NCAA website for data and generates some rudimentary analyses.

The Deep Fij Modelling Framework
================================

Analysis
--------

```scala
trait Analysis[T] {
  def analyze(season: Season): (LocalDate, Team)=>Option[T]
}
```

At the highest level of generality, analysis constist of taking a seasons worth of data and converting using it to synthesize a function from a tuple of team and date to a type T.

Typically when one considers analyses the type T is assumed to be numeric, but it need not be.  In fact if we treat T with greater generality, we can use analyses to generate further analyses.

def map(f:T=>U): Analysis[U] 
def zip(f:(T,U)=>V): Analysis[V]

With those two definitions, consider constructing an analysis of "winning percentage"
```scala
class Games extends Analysis[List[Game]] {
   season  
}
``` 


Prediction
----------

```scala
trait Predictor[T] {
  def predict(g:Game):T
  def error(t:T,g:Game):Double
}
```

```scala
trait FavoritePredictor extends Predictor[Team]
```
```scala
trait WinProbPredictor extends Predictor[Double]
```
```scala
trait SpreadPredictor extends Predictor[Double]
```
```scala
trait SpreadDistributionPredictor extends Predictor[Int=>Double]
```
```scala
trait ScorePredictor extends Predictor[(Double, Double)]
```
```scala
trait ScoreDistributionPredictor extends Predictor[(Int, Int)=>Double)]
```
