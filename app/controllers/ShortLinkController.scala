package controllers

import akka.actor.ActorSystem
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.util.Timeout
import com.typesafe.config.Config
import model.IdGenerator
import model.shortLink.ShortLink
import play.api.Logging
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

@Singleton
class ShortLinkController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem, config: Config)(implicit ec: ExecutionContext)
  extends AbstractController(cc) with Logging {

  implicit val timeout: Timeout = 20.seconds
  implicit val scheduler: Scheduler = actorSystem.toTyped.scheduler

  def postShortLinks: Action[AnyContent] = Action.async {
    val id = IdGenerator.base58Id()
    val ref = actorSystem.spawn(ShortLink(id, config), id)
    ref.ask(v => ShortLink.Commands.Create(v))
      .map{
        case ShortLink.Commands.Create.Results.Created => Accepted(id)
        case ShortLink.Commands.Create.Results.AlreadyExists => InternalServerError(id)
        case _ => InternalServerError
      }
  }
}
