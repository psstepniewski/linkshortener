package projection.shortLink

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.{JdbcHandler, JdbcProjection}
import akka.projection.scaladsl.{AtLeastOnceProjection, SourceProvider}
import model.shortLink.ShortLink
import play.api.Logging
import ShortLinkProjection.EventHandler
import projection.JdbcSessionFactory

import scala.concurrent.duration._
import javax.inject.{Inject, Singleton}

@Singleton
class ShortLinkProjection @Inject()(actorSystem: ActorSystem, jdbcSessionFactory: JdbcSessionFactory){

  private val sourceProvider: SourceProvider[Offset, EventEnvelope[ShortLink.Event]] =
    EventSourcedProvider
      .eventsByTag[ShortLink.Event](actorSystem.toTyped, readJournalPluginId = JdbcReadJournal.Identifier, tag = ShortLink.entityType)

  private val projection: AtLeastOnceProjection[Offset, EventEnvelope[ShortLink.Event]] =
    JdbcProjection.atLeastOnce(
        projectionId = ProjectionId(ShortLink.entityType, "postgres"),
        sourceProvider,
        () => jdbcSessionFactory.create(),
        handler = () => new EventHandler())(actorSystem.toTyped
    )
    .withSaveOffset(afterEnvelopes = 100, afterDuration = 500.millis)

  actorSystem.spawn(ProjectionBehavior(projection), projection.projectionId.id)
}

object ShortLinkProjection {

  class EventHandler() extends JdbcHandler[EventEnvelope[ShortLink.Event], JdbcSessionFactory.JdbcSession] with Logging {

    override def process(session: JdbcSessionFactory.JdbcSession, envelope: EventEnvelope[ShortLink.Event]): Unit = {
      envelope.event match {
        case anyEvent =>
          logger.info(s"ShortLinkProjection receives $anyEvent.")
      }
    }
  }
}
