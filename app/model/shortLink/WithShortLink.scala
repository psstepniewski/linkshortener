package model.shortLink

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.util.Timeout
import com.typesafe.config.Config
import model.shortLink.WithShortLink.ShortLinkFactory.Commands.{GetRef, RefTerminated}
import model.shortLink.WithShortLink.{ShortLinkFactory, getShortLinkFactory}
import play.api.Logging

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}


trait WithShortLink {
  self: Logging =>

  def actorSystem: ActorSystem

  protected val shortLinkFactory: ActorRef[ShortLinkFactory.Command] = getShortLinkFactory(actorSystem)

  def shortLink(id: String, config: Config)(implicit timeout: Timeout, scheduler: Scheduler, ec: ExecutionContext): Future[ActorRef[ShortLink.Command]] = {
    shortLinkFactory.ask(replyTo => GetRef(id, ShortLink(id, config), replyTo)).map{
      case v: GetRef.Results.Ref =>
        v.ref
      case e =>
        throw new RuntimeException(s"WithShortLink received unknown command[$e].")
    }
  }
}

object WithShortLink {
  private var shortLinkFactoryOpt: Option[ActorRef[ShortLinkFactory.Command]] = None

  private def getShortLinkFactory(actorSystem: ActorSystem): ActorRef[ShortLinkFactory.Command] = {
    if(shortLinkFactoryOpt.nonEmpty) {
      shortLinkFactoryOpt.get
    }
    else {
      val factoryRef = actorSystem.spawn(ShortLinkFactory(), "ShortLinkFactory")
      shortLinkFactoryOpt = Some(factoryRef)
      factoryRef
    }
  }

  object ShortLinkFactory {

    val childs: mutable.Map[String, ActorRef[ShortLink.Command]] = mutable.Map()

    sealed trait Command
    object Commands {
      case class GetRef(id: String, behavior: Behavior[ShortLink.Command], replyTo: ActorRef[GetRef.Result]) extends Command
      case class RefTerminated(id: String) extends Command

      object GetRef {
        sealed trait Result
        object Results {
          case class Ref(ref: ActorRef[ShortLink.Command]) extends Result
        }
      }
    }

    def apply(): Behavior[Command] = Behaviors.setup { implicit context =>
      Behaviors.receiveMessage {
        case c: GetRef =>
          val ref = getOrSpawn(c.id, c.behavior)
          childs.put(c.id, ref)
          context.watchWith(ref, RefTerminated(c.id))
          c.replyTo ! GetRef.Results.Ref(ref)
          Behaviors.same
        case c: RefTerminated =>
          context.log.debug(s"ShortLinkFactory removes Child[id=${c.id}].", c)
          childs.remove(c.id)
          Behaviors.same
        case c =>
          context.log.warn("ShortLinkFactory received unknown command[{}].", c)
          Behaviors.same
      }
    }

    private def getOrSpawn(id: String, behavior: Behavior[ShortLink.Command])(implicit context: ActorContext[Command]): ActorRef[ShortLink.Command] = childs.get(id) match {
      case Some(v) =>
        context.log.debug(s"Child[id={}] exists. Factory returns the one.", id)
        v
      case None =>
        context.log.debug(s"Child[id={}] not found. Factory spawns new one.", id)
        context.spawn(behavior, id)
    }
  }
}


