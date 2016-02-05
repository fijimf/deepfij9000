package modules

import com.google.inject.AbstractModule
import modules.scraping.ScrapingActor
import play.api.libs.concurrent.AkkaGuiceSupport

class ScrapingModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[ScrapingActor]("data-load-actor")
  }
}
