package controllers

import akka.actor.ActorSystem
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import model.shortLink.ShortLink
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

@Singleton
class ShortLinkController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  implicit val timeout: Timeout = 20.seconds
  implicit val scheduler: Scheduler = actorSystem.toTyped.scheduler

  def test: Action[AnyContent] = Action.async {
    val id = UUID.randomUUID().toString
    val ref = actorSystem.spawn(ShortLink(id), id)
    ref.ask(v => ShortLink.Commands.Create(v))
      .map{
        case ShortLink.Commands.Create.Results.Created => Accepted
        case _ => InternalServerError
      }
  }
}
