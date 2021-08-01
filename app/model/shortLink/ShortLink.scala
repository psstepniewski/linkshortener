package model.shortLink

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import model.CborSerializable
import model.shortLink.ShortLink.Commands.Create
import model.shortLink.ShortLink.Events.Created

import java.time.Instant

object ShortLink {

  val entityType = "ShortLink"

  sealed trait Command extends CborSerializable
  object Commands {
    case class Create(replyTo: ActorRef[Create.Result]) extends Command
    case object Create {
      sealed trait Result extends CborSerializable
      object Results {
        case object Created extends Result
      }
    }
  }
  sealed trait Event extends CborSerializable
  object Events {
    case class Created(shortLinkId: String) extends Event
  }

  case class State(shortLinkId: String, shortLinkDomain: String, shortLinkUrl: String, createdTimestamp: Instant)

  sealed trait Entity {
    def state: State
    def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, Entity]
    def applyEvent(entity: Entity, event: Event)(implicit context: ActorContext[Command]): Entity
  }

  case class EmptyShortLink(id: String) extends Entity {

    override def state: State = throw new IllegalStateException(s"EmptyShortLink[$id] has not approved state yet.")

    override def applyCommand(cmd: Command)(implicit context: ActorContext[Command]): ReplyEffect[Event, Entity] = cmd match {
      case c: Create =>
        Effect.persist(Events.Created(id)).thenReply(c.replyTo)(_ => Create.Results.Created)
      case c =>
        context.log.warn("{}[id={}, state=Empty] unknown command[{}].", entityType, id, c)
        Effect.noReply
    }

    override def applyEvent(entity: Entity, event: Event)(implicit context: ActorContext[Command]): Entity = event match {
      case e: Created =>
        EmptyShortLink(e.shortLinkId)
      case e =>
        context.log.warn(s"{}[id={}, state=Empty] received unexpected event[{}]", entityType, id, e)
        entity
    }
  }

  def apply(id: String): Behavior[Command] = Behaviors.setup { implicit context =>
    context.log.debug2("Starting entity actor {}[id={}]", entityType, id)
    EventSourcedBehavior.withEnforcedReplies[Command, Event, Entity](
      PersistenceId.of("ShortLink", id),
      EmptyShortLink(id),
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
