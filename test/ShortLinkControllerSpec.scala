import ShortLinkControllerSpec.PostShortLinks
import ShortLinkControllerSpec.PostShortLinks.{Response, requestWrites}
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.util.Timeout
import controllers.ShortLinkController
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.http.Status.OK
import play.api.libs.json.{JsSuccess, Json, Reads, Writes}
import play.api.routing.Router
import play.api.test.Helpers.{contentAsJson, contentType, route, status}
import play.api.test.{FakeRequest, Injecting}
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents}

import scala.concurrent.duration._

class ShortLinkControllerSpec extends PlaySpec with OneAppPerSuiteWithComponents with GivenWhenThen {

  implicit val timeout: Timeout = 5.seconds

  override def components: BuiltInComponents =  new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

    val shortLinkController = new ShortLinkController(controllerComponents, actorSystem, configuration.underlying)(executionContext)
    import play.api.routing.sird._

    lazy val router: Router = Router.from({
      case POST(p"/api/v1/short_links") => shortLinkController.postShortLinks
    })

    override lazy val configuration: Configuration = Configuration(PersistenceTestKitPlugin.config).withFallback(context.initialConfiguration)
  }

  "ShortLinkController#postShortLinks" should {
    "return new short link" in  {
      Given("fake request with originalLinkUrl")
      val request = FakeRequest("POST", "/api/v1/short_links")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(PostShortLinks.Request("https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/")))

      When("postShortLinks is requested")
      val result = route(app, request).get

      Then("response should contain json body with shortLinkUrl")
      status(result) must equal(OK)
      contentType(result) must contain("application/json")
      contentAsJson(result).validate[Response] mustBe a[JsSuccess[_]]
    }
  }
}

object ShortLinkControllerSpec {
  object PostShortLinks {
    case class Request(originalLinkUrl: String)
    implicit val requestWrites: Writes[Request] = Json.writes[Request]

    case class Response(shortLinkId: String, shortLinkUrl: String)
    implicit val responseReads: Reads[Response] = Json.reads[Response]
  }
}
