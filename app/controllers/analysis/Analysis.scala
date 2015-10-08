package controllers.analysis

import java.time.LocalDate

import controllers.model.{Team, Season, Ncaa}

trait Analysis[T] {
  self =>
  def analyze(ncaa: Ncaa, season: Season): (Team, LocalDate) => Option[T]

  def map[U](fn: T => U): Analysis[U] = {
    new Analysis[U] {
      override def analyze(ncaa: Ncaa, season: Season): (Team, LocalDate) => Option[U] = (t, d) => {
        self.analyze(ncaa, season).apply(t, d).map(fn)
      }
    }
  }

  def flatMap[U](fn: T => Option[U]): Analysis[U] = {
    new Analysis[U] {
      override def analyze(ncaa: Ncaa, season: Season): (Team, LocalDate) => Option[U] = (t, d) => {
        self.analyze(ncaa, season).apply(t, d).flatMap(fn)
      }
    }
  }

  def zip[U, V](u: Analysis[U], fn: (T, U) => V): Analysis[V] = {
    new Analysis[V] {
      override def analyze(ncaa: Ncaa, season: Season): (Team, LocalDate) => Option[V] = (t, d) => {
        for (at <- self.analyze(ncaa, season).apply(t, d);
             au <- u.analyze(ncaa, season).apply(t, d)) yield fn(at, au)
      }
    }
  }
}

