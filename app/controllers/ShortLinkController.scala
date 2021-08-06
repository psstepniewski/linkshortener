package controllers

import akka.actor.ActorSystem
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.util.Timeout
import com.typesafe.config.Config
import controllers.ShortLinkController.PostShortLinks
import model.IdGenerator
import model.shortLink.ShortLink
import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, Reads}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Singleton
class ShortLinkController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem, config: Config)(implicit ec: ExecutionContext)
  extends AbstractController(cc) with Logging {

  implicit val timeout: Timeout = 20.seconds
  implicit val scheduler: Scheduler = actorSystem.toTyped.scheduler

  def postShortLinks: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body.validate[PostShortLinks.Request] match {
      case JsSuccess(req, _) =>
        val id = IdGenerator.base58Id()
        val ref = actorSystem.spawn(ShortLink(id, config), id)
        ref.ask(replyTo => ShortLink.Commands.Create(req.originalLinkUrl, replyTo))
          .map{
            case v: ShortLink.Commands.Create.Results.Created => Accepted(v.shortLinkUrl)
            case ShortLink.Commands.Create.Results.AlreadyExists => InternalServerError(id)
            case _ => InternalServerError
          }
      case JsError(errors) =>
        Future.successful(BadRequest(errors.mkString(",")))
    }
  }
}

object ShortLinkController {

  object PostShortLinks {
    case class Request(originalLinkUrl: String)
    implicit val requestWrites: Reads[Request] = Json.reads[Request]
  }

  def getShortLink(shortLinkId: String) = Action.async {
    val ref = actorSystem.spawn(ShortLink(shortLinkId, config), shortLinkId)
  }
}
