package projection.shortLink

import akka.actor.ActorSystem
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.{JdbcHandler, JdbcProjection}
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import anorm.SqlStringInterpolation
import model.shortLink.ShortLink
import play.api.Logging
import projection.JdbcSessionFactory
import projection.shortLink.ShortLinkProjection.EventHandler

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

@Singleton
class ShortLinkProjection @Inject()(actorSystem: ActorSystem, jdbcSessionFactory: JdbcSessionFactory){

  private val sourceProvider: SourceProvider[Offset, EventEnvelope[ShortLink.Event]] =
    EventSourcedProvider
      .eventsByTag[ShortLink.Event](actorSystem.toTyped, readJournalPluginId = JdbcReadJournal.Identifier, tag = ShortLink.TypeKey.name)

  private val projection: ExactlyOnceProjection[Offset, EventEnvelope[ShortLink.Event]] =
    JdbcProjection.exactlyOnce(
        projectionId = ProjectionId(ShortLink.TypeKey.name, "postgres"),
        sourceProvider,
        () => jdbcSessionFactory.create(),
        handler = () => new EventHandler()
    )(actorSystem.toTyped)
    .withRestartBackoff(1.seconds, 10.seconds, 0.2)

  ClusterSingleton(actorSystem.toTyped).init(SingletonActor(
    Behaviors.supervise(ProjectionBehavior(projection)).onFailure(SupervisorStrategy.restartWithBackoff(1.second, 10.seconds, 0.2)),
    projection.projectionId.id
  ))
}

object ShortLinkProjection {

  class EventHandler() extends JdbcHandler[EventEnvelope[ShortLink.Event], JdbcSessionFactory.JdbcSession] with Logging {

    override def process(session: JdbcSessionFactory.JdbcSession, envelope: EventEnvelope[ShortLink.Event]): Unit = {
      logger.info(s"ShortLinkProjection receives ${envelope.event}.")
      envelope.event match {
        case e: ShortLink.Events.Created =>
          session.withConnection(implicit conn => {
            SQL"""
              insert into short_links(short_link_id, short_link_domain, short_link_url, original_link_url, tags, created_timestamp)
              values (${e.shortLinkId}, ${e.shortLinkDomain}, ${e.shortLinkUrl}, ${e.originalLinkUrl}, ${e.tags.mkString(",")}, ${e.timestamp})
            """.executeInsert()
          })
        case e: ShortLink.Events.Clicked =>
          session.withConnection(implicit conn => {
            SQL"""
              insert into short_link_clicks(short_link_id, user_agent_header, x_forwarded_for_header, referer_header, created_timestamp)
              values (${e.shortLinkId}, ${e.userAgentHeader}, ${e.xForwardedForHeader}, ${e.refererHeader}, ${e.timestamp})
            """.executeInsert()
          })
        case other =>
          logger.info(s"ShortLinkProjection receives $other.")
      }
    }
  }
}
