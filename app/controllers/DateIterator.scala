package controllers

import org.joda.time.LocalDate

object DateIterator {
  def apply(s: LocalDate, e: LocalDate): Iterator[LocalDate] = new Iterator[LocalDate] {
    var d = s

    override def hasNext: Boolean = d.isBefore(e)

    override def next(): LocalDate = {
      d = d.plusDays(1)
      d
    }
  }
}
