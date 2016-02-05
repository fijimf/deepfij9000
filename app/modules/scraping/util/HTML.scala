package modules.scraping.util

import java.io.{Reader, StringReader}

import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import org.xml.sax.InputSource

import scala.util.Try
import scala.xml.Node
import scala.xml.parsing.NoBindingFactoryAdapter

object HTML {
  def loadReader(r: Reader): Try[Node] = Try {
    new NoBindingFactoryAdapter().loadXML(new InputSource(r), new SAXFactoryImpl().newSAXParser())
  }

  def loadString(s: String): Try[Node] = loadReader(new StringReader(s))
}
