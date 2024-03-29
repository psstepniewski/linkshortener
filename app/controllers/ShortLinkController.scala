package controllers

import akka.actor.ActorSystem
import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.util.{ByteString, Timeout}
import controllers.ShortLinkController.PostShortLinks
import model.IdGenerator
import model.shortLink.{ShortLink, ShortLinkSharding}
import play.api.Logging
import play.api.http.Writeable
import play.api.libs.json._
import play.api.mvc._

import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShortLinkController @Inject()(idGenerator: IdGenerator, shortLinkSharding: ShortLinkSharding, cc: ControllerComponents, actorSystem: ActorSystem)(implicit ec: ExecutionContext)
  extends AbstractController(cc) with Logging {

  implicit val timeout: Timeout = 20.seconds
  implicit val scheduler: Scheduler = actorSystem.toTyped.scheduler

  def postShortLinks: Action[JsValue] = Action(parse.json).async { implicit request =>
    def iterate(originalLinkUrl: String, tags: Set[String], tryNumber: Int = 1, maxTriesNumber: Int = 3): Future[Result] = {
      logger.info(s"ShortLinkController#postShortLinks: create new ShortLink[tryNumber=$tryNumber, maxTriesNumber=$maxTriesNumber] for OriginalLink[url=$originalLinkUrl].")
      if(tryNumber > maxTriesNumber) {
        logger.warn(s"ShortLinkController#postShortLinks: tryNumber[$tryNumber] > maxTriesNumber[$maxTriesNumber]. Returning 500.")
        Future.successful(InternalServerError)
      }
      else {
        val id = idGenerator.newId
        shortLinkSharding.entityRefFor(id).ask(replyTo => ShortLink.Commands.Create(originalLinkUrl, tags, replyTo))
          .flatMap {
            case v: ShortLink.Commands.Create.Results.Created =>
              logger.info(s"ShortLinkController#postShortLinks: new ShortLink[id=$id] created. Returning 200.")
              Future.successful(Ok(PostShortLinks.Response(v.shortLinkId, v.shortLinkUrl)))
            case ShortLink.Commands.Create.Results.AlreadyExists =>
              logger.info(s"ShortLinkController#postShortLinks: Id[$id] taken. Starts new Iteration[tryNumber=$tryNumber].")
              iterate(originalLinkUrl, tags, tryNumber + 1)
            case e =>
              logger.error(s"ShortLinkController#postShortLinks: Got unexpected message[$e]. Returning 500.")
              Future.successful(InternalServerError)
          }
          .recover{case e =>
            logger.error(s"ShortLinkController#postShortLinks: unexpected error. Returning 500.", e)
            InternalServerError
          }
      }
    }

    request.body.validate[PostShortLinks.Request] match {
      case JsSuccess(req, _) =>
        iterate(req.originalLinkUrl, req.tags)
          .recover{case e =>
            logger.error(s"ShortLinkController#postShortLinks: unexpected error. Returning 500.", e)
            InternalServerError
          }
      case JsError(errors) =>
        logger.error(s"ShortLinkController#postShortLinks: cannot parse json to Request object. Returning 400. Errors: $errors")
        Future.successful(BadRequest(errors.mkString(",")))
    }
  }

  def getShortLink(shortLinkId: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"ShortLinkController#getShortLink: ShortLink[id=$shortLinkId] clicked, get OriginalLink url.")
    val userAgentHeader = request.headers.get(USER_AGENT)
    val xForwardedForHeader = request.headers.get(X_FORWARDED_FOR)
    val refererHeader = request.headers.get(REFERER)
    shortLinkSharding.entityRefFor(shortLinkId).ask(replyTo => ShortLink.Commands.Click(userAgentHeader, xForwardedForHeader, refererHeader, replyTo))
      .map {
        case v: ShortLink.Commands.Click.Results.RedirectTo =>
          logger.info(s"ShortLinkController#getShortLink: OriginalLink for ShortLink[id=$shortLinkId] found. Returning 303 to OriginalLink url.")
          Redirect(v.originalLinkUrl)
        case ShortLink.Commands.Click.Results.NotFound =>
          logger.info(s"ShortLinkController#getShortLink: OriginalLink for ShortLink[id=$shortLinkId] NOT found. Returning 404.")
          NotFound
        case e =>
          logger.error(s"ShortLinkController#getShortLink: Got unexpected message[$e]. Returning 500.")
          InternalServerError
      }
      .recover{case e =>
        logger.error(s"ShortLinkController#getShortLink: unexpected error. Returning 500.", e)
        InternalServerError
      }
  }
}

object ShortLinkController {

  private[ShortLinkController] object PostShortLinks {
    case class Request(originalLinkUrl: String, tags: Set[String])
    implicit val requestReads: Reads[Request] = Json.reads[Request]

    case class Response(shortLinkId: String, shortLinkUrl: String)
    implicit val responseWrites: Writeable[PostShortLinks.Response] = Writeable[PostShortLinks.Response](r => ByteString(Json.writes[Response].writes(r).toString(), StandardCharsets.UTF_8), Some("application/json"))
  }
}
