package model.shortLink

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.typesafe.config.Config
import model.CborSerializable
import model.shortLink.ShortLink.Commands.{Create, Click}
import model.shortLink.ShortLink.Events.{Created, Clicked}

import java.time.Instant

object ShortLink {

  val entityType = "ShortLink"

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
  }
  sealed trait Event extends CborSerializable
  object Events {
    case class Created(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String, timestamp: Instant = Instant.now()) extends Event
    case class Clicked(shortLinkId: String, userAgentHeader: Option[String], xForwardedForHeader: Option[String], timestamp: Instant = Instant.now()) extends Event
  }

  case class State(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, originalLinkUrl: String)

  sealed trait Entity extends CborSerializable {
    def state: State
    def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, Entity]
    def applyEvent(entity: Entity, event: Event)(implicit context: ActorContext[Command]): Entity
  }

  case class EmptyShortLink(id: String, shortLinkDomain: String) extends Entity {

    override def state: State = throw new IllegalStateException(s"EmptyShortLink[$id] has not approved state yet.")

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, Entity] = cmd match {
      case c: Create =>
        val url = s"$shortLinkDomain${controllers.routes.ShortLinkController.getShortLink(id).url}"
        Effect.persist(Events.Created(id, shortLinkDomain, url, c.originalLinkUrl))
          .thenReply(c.replyTo)(_ => Create.Results.Created(id, url))
      case c: Click =>
        Effect.reply(c.replyTo)(Click.Results.NotFound)
      case c =>
        context.log.warn("{}[id={}, state=Empty] received unknown command[{}].", entityType, id, c)
        Effect.noReply
    }

    override def applyEvent(entity: Entity, event: Event)(implicit context: ActorContext[Command]): Entity = event match {
      case e: Created =>
        ShortLink(e.shortLinkId, State(id, e.shortLinkDomain, e.shortLinkUrl, e.originalLinkUrl), shortLinkDomain)
      case e =>
        context.log.warn(s"{}[id={}, state=Empty] received unexpected event[{}]", entityType, id, e)
        entity
    }
  }

  case class ShortLink(id: String, state: State, shortLinkDomain: String) extends Entity {

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, Entity] = cmd match {
      case c: Create =>
        Effect.reply(c.replyTo)(Create.Results.AlreadyExists)
      case c: Click =>
        Effect.persist(Events.Clicked(id, c.userAgentHeader, c.xForwardedForHeader))
          .thenReply(c.replyTo)(_ => Click.Results.RedirectTo(state.originalLinkUrl))
      case c =>
        context.log.warn("{}[id={}] unknown command[{}].", entityType, id, c)
        Effect.noReply
    }

    override def applyEvent(entity: Entity, event: Event)(implicit context: ActorContext[Command]): Entity = event match {
      case _: Clicked =>
        //do nothing
        entity
      case e =>
        context.log.warn(s"{}[id={}] received unexpected event[{}]", entityType, id, e)
        entity
    }
  }

  def apply(id: String, config: Config): Behavior[Command] = Behaviors.setup { implicit context =>
    context.log.debug2("Starting entity actor {}[id={}]", entityType, id)
    EventSourcedBehavior.withEnforcedReplies[Command, Event, Entity](
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
