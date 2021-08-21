package model.shortLink

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.typesafe.config.Config
import model.CborSerializable
import model.shortLink.ShortLink.Commands.{Click, Create, ReceiveTimeout}
import model.shortLink.ShortLink.Events.{Clicked, Created}

import java.time.Instant
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object ShortLink {

  private val entityType = "ShortLink"
  private val receiveTimeout: FiniteDuration = 30.seconds

  sealed trait Command extends CborSerializable
  object Commands {
    case class Create(originalLinkUrl: String, replyTo: ActorRef[Create.Result]) extends Command
    case object Create {
      sealed trait Result extends CborSerializable
      object Results {
        case class Created(shortLinkId: String, shortLinkUrl: String) extends Result
        case object AlreadyExists extends Result
      }
    }
    case class Click(userAgentHeader: Option[String], xForwardedForHeader: Option[String], replyTo: ActorRef[Click.Result]) extends Command
    case object Click {
      sealed trait Result extends CborSerializable
      object Results {
        case class RedirectTo(originalLinkUrl: String) extends Result
        case object NotFound extends Result
      }
    }
    case object ReceiveTimeout extends Command
  }
  sealed trait Event extends CborSerializable
  object Events {
    case class Created(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String, timestamp: Instant = Instant.now()) extends Event
    case class Clicked(shortLinkId: String, userAgentHeader: Option[String], xForwardedForHeader: Option[String], timestamp: Instant = Instant.now()) extends Event
  }

  case class Snapshot(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String)

  sealed trait State extends CborSerializable {
    def snapshot: Snapshot
    def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State]
    def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State
  }

  case class EmptyShortLink(id: String, shortLinkDomain: String) extends State {

    override def snapshot: Snapshot = throw new IllegalStateException(s"EmptyShortLink[$id] has not approved state yet.")

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State] = cmd match {
      case c: Create =>
        val url = s"$shortLinkDomain${controllers.routes.ShortLinkController.getShortLink(id).url}"
        Effect.persist(Events.Created(id, shortLinkDomain, url, c.originalLinkUrl))
          .thenReply(c.replyTo)(_ => Create.Results.Created(id, url))
      case c: Click =>
        Effect.stop()
          .thenReply(c.replyTo)(_ => Click.Results.NotFound)
      case ReceiveTimeout =>
        Effect.stop()
          .thenNoReply()
      case c =>
        context.log.warn("{}[id={}, state=Empty] received unknown command[{}].", entityType, id, c)
        Effect.stop()
          .thenNoReply()
    }

    override def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State = event match {
      case e: Created =>
        ShortLink(e.shortLinkId, Snapshot(id, e.shortLinkDomain, e.shortLinkUrl, e.originalLinkUrl), shortLinkDomain)
      case e =>
        context.log.warn(s"{}[id={}, state=Empty] received unexpected event[{}]", entityType, id, e)
        state
    }
  }

  case class ShortLink(id: String, snapshot: Snapshot, shortLinkDomain: String) extends State {

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, State] = cmd match {
      case c: Create =>
        Effect.reply(c.replyTo)(Create.Results.AlreadyExists)
      case c: Click =>
        Effect.persist(Events.Clicked(id, c.userAgentHeader, c.xForwardedForHeader))
          .thenReply(c.replyTo)(_ => Click.Results.RedirectTo(snapshot.originalLinkUrl))
      case ReceiveTimeout =>
        Effect.stop()
          .thenNoReply()
      case c =>
        context.log.warn("{}[id={}] unknown command[{}].", entityType, id, c)
        Effect.noReply
    }

    override def applyEvent(state: State, event: Event)(implicit context: ActorContext[Command]): State = event match {
      case _: Clicked =>
        //do nothing
        state
      case e =>
        context.log.warn(s"{}[id={}] received unexpected event[{}]", entityType, id, e)
        state
    }
  }

  def apply(id: String, config: Config): Behavior[Command] = Behaviors.setup { implicit context =>
    context.log.debug2("Starting entity actor {}[id={}]", entityType, id)
    context.setReceiveTimeout(receiveTimeout, ReceiveTimeout)
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      PersistenceId.of("ShortLink", id),
      EmptyShortLink(id, config.getString("linkshortener.shortLink.domain")),
      (state, cmd) => {
        context.log.debug("{}[id={}] receives command {}", entityType, id, cmd)
        state.applyCommand(cmd)
      },
      (state, event) => {
        context.log.debug("{}[id={}] persists event {}", entityType, id, event)
        state.applyEvent(state, event)
      }
    ).withTagger(_ => Set("linkshortener", entityType, "v1"))
  }
}
