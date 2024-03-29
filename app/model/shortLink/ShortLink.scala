package model.shortLink

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.typesafe.config.Config
import model.CborSerializable
import model.shortLink.ShortLink.Commands.{Click, Create, Passivate}
import model.shortLink.ShortLink.Events.{Clicked, Created}

import java.time.Instant
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ShortLink {

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShortLink")

  private val receiveTimeout: FiniteDuration = 30.seconds

  sealed trait Command extends CborSerializable
  object Commands {
    case class Create(originalLinkUrl: String, tags: Set[String], replyTo: ActorRef[Create.Result]) extends Command
    case object Create {
      sealed trait Result extends CborSerializable
      object Results {
        case class Created(shortLinkId: String, shortLinkUrl: String) extends Result
        case object AlreadyExists extends Result
      }
    }
    case class Click(userAgentHeader: Option[String], xForwardedForHeader: Option[String], refererHeader: Option[String], replyTo: ActorRef[Click.Result]) extends Command
    case object Click {
      sealed trait Result extends CborSerializable
      object Results {
        case class RedirectTo(originalLinkUrl: String) extends Result
        case object NotFound extends Result
      }
    }
    case object Passivate extends Command
  }
  sealed trait Event extends CborSerializable
  object Events {
    case class Created(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String, tags: Set[String], timestamp: Instant = Instant.now()) extends Event
    case class Clicked(shortLinkId: String, userAgentHeader: Option[String], xForwardedForHeader: Option[String], refererHeader: Option[String], timestamp: Instant = Instant.now()) extends Event
  }

  case class Snapshot(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String)

  sealed trait State extends CborSerializable {
    def snapshot: Snapshot
    def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State]
    def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State
    def shard: ActorRef[ClusterSharding.ShardCommand]
  }

  case class EmptyState(id: String, shard: ActorRef[ClusterSharding.ShardCommand], shortLinkDomain: String) extends State {

    override def snapshot: Snapshot = throw new IllegalStateException(s"EmptyShortLink[$id] has not approved state yet.")

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State] = cmd match {
      case c: Create =>
        val url = s"$shortLinkDomain/$id"
        Effect.persist(Events.Created(id, shortLinkDomain, url, c.originalLinkUrl, c.tags))
          .thenReply(c.replyTo)(_ => Create.Results.Created(id, url))
      case c: Click =>
        context.self ! Passivate
        Effect.reply(c.replyTo)(Click.Results.NotFound)
      case Passivate =>
        shard ! ClusterSharding.Passivate(context.self)
        Effect.noReply
      case c =>
        context.log.warn("{}[id={}, state=Empty] received unknown command[{}].", TypeKey.name, id, c)
        context.self ! Passivate
        Effect.noReply
    }

    override def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State = event match {
      case e: Created =>
        ActiveState(e.shortLinkId, state.shard, Snapshot(id, e.shortLinkDomain, e.shortLinkUrl, e.originalLinkUrl), shortLinkDomain)
      case e =>
        context.log.warn(s"{}[id={}, state=Empty] received unexpected event[{}]", TypeKey.name, id, e)
        state
    }
  }

  case class ActiveState(id: String, shard: ActorRef[ClusterSharding.ShardCommand], snapshot: Snapshot, shortLinkDomain: String) extends State {

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State] = cmd match {
      case c: Create =>
        Effect.reply(c.replyTo)(Create.Results.AlreadyExists)
      case c: Click =>
        Effect.persist(Events.Clicked(id, c.userAgentHeader, c.xForwardedForHeader, c.refererHeader))
          .thenReply(c.replyTo)(_ => Click.Results.RedirectTo(snapshot.originalLinkUrl))
      case Passivate =>
        shard ! ClusterSharding.Passivate(context.self)
        Effect.noReply
      case c =>
        context.log.warn("{}[id={}] unknown command[{}].", TypeKey.name, id, c)
        Effect.noReply
    }

    override def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State = event match {
      case _: Clicked =>
        //do nothing
        state
      case e =>
        context.log.warn(s"{}[id={}] received unexpected event[{}]", TypeKey.name, id, e)
        state
    }
  }

  def apply(id: String, shard: ActorRef[ClusterSharding.ShardCommand], config: Config): Behavior[Command] = Behaviors.setup { implicit context =>
    context.log.debug2("Starting entity actor {}[id={}]", TypeKey.name, id)
    context.setReceiveTimeout(receiveTimeout, Passivate)
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId(id),
      EmptyState(id, shard, config.getString("linkshortener.shortLink.domain")),
      (state, cmd) => {
        context.log.debug("{}[id={}] receives command {}", TypeKey.name, id, cmd)
        state.applyCommand(cmd)
      },
      (state, event) => {
        context.log.debug("{}[id={}] persists event {}", TypeKey.name, id, event)
        state.applyEvent(state, event)
      }
    )
    .withTagger(_ => Set("linkshortener", TypeKey.name, "v1"))
    .receiveSignal {
      case (_, PostStop) => context.log.info(s"ShortLink[id=$id] receives PostStop signal")
    }
  }

  def persistenceId(id: String): PersistenceId = PersistenceId.of(TypeKey.name, id)
}
