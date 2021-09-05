package projection

import com.google.inject.AbstractModule
import play.api.Logging
import projection.shortLink.ShortLinkProjection

class ProjectionModule extends AbstractModule with Logging {

  override def configure(): Unit = {
    logger.info("ProjectionModule starts.")
    bind(classOf[ShortLinkProjection]).asEagerSingleton()
  }
}
