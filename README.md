Deep Fij 9000
=============

This is the latest iteration of my college basketball analysis website, implemented with the Play Framework.

Basically scrapes the NCAA website for data and generates some rudimentary analyses.

The Deep Fij Modelling Framework
================================
The Data Model
--------------

The focus of the data model is the `Season`.  

A `Season` is identified by its `year`, and can be interrogated to return all of the `game`s within that `season` and all of the `result`s of `game`s which have been completed.  Given the `key` (a `String`) of a `team`, the `season` can be interrogated to identify the `conference` of the `team` represented by the `key`.  A `season` can be interrogated to privide a `List[LocalDate]`of dates on which `game`s occurred, as well as a `List[Team]` of `team`s which are participating in `game`s. 

A `Game` conssts of `date`, `time` and `location` of the game, the `key` of the home and away `team`s, whether the game was played at a neutral site, whether it was a conference tournament game, whether it was an NCAA tournament game (and the seeds of the teams if it was).  

A `Result` contains the final scores for the home and away team as well as the number of periods the game took.

A `Team` consists of a key, a name, and other reference information about the team, for example nickname, colors, official URL, etc.  Note that conference is season dependent, and thus not stored as part of the team.

A `Conference` consists of a key, a name, and potentially other reference data about the conference.  It does not contain the memeber teams.

Analysis
--------
```scala
trait Analysis[T] {
  def analyze(season: Season): (LocalDate, Team)=>Option[T]
}
```

At the highest level of generality, analysis constist of taking a season's worth of data and using it to synthesize a function of team and date to a t of type T.

A Season can communicate its data Seq[LocalDate] as well as the list of teams Seq[Team].  As such the domain of the resultant function is discrete and limited.  This leads to opportunities for both memoization and caching.

Typically when one considers analyses the type T is assumed to be numeric, but it need not be.  In fact if we treat T with greater generality, we can use analyses to generate further analyses.
```scala
def map(f:T=>U): Analysis[U] 
def flatMap(f:T=>Option[U]: Analysis[U]
def zip(a:Analysis[U], f:(T,U)=>V): Analysis[V]
```
With those two definitions, consider constructing an analysis of "winning percentage"
```scala
class Games extends Analysis[List[Game]] {
   season  
}
``` 

Rating and Ranking
------------------



If the type T of the analysis is is an Ordered, 

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


Moving from Prediction to Rating
--------------------------------


Moving form Rating to Prediction
--------------------------------

A Note on Architecture
----------------------

It's useful to consider the whole as a suite of separate applications, united mostly in sharing a data store.

1. The Scraper - responsible for communiating with 3rd party websites to download information and push to the data store
1. The Analyzer - Periodically generates analyses and pushes these to the data store
1. The Website - responsible for synthesizing useful views out of the data store
