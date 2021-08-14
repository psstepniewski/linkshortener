import ShortLinkControllerSpec.PostShortLinks
import ShortLinkControllerSpec.PostShortLinks.{Response, requestWrites}
import akka.util.Timeout
import controllers.ShortLinkController
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.http.Status.{NOT_FOUND, OK, SEE_OTHER}
import play.api.libs.json.{JsSuccess, Json, Reads, Writes}
import play.api.routing.Router
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentType, header, route, status}
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents}

import scala.concurrent.duration._

class ShortLinkControllerSpec extends PlaySpec with OneAppPerSuiteWithComponents with GivenWhenThen {

  implicit val timeout: Timeout = 5.seconds

  override def components: BuiltInComponents =  new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

    val shortLinkController = new ShortLinkController(controllerComponents, actorSystem, configuration.underlying)(executionContext)
    import play.api.routing.sird._

    lazy val router: Router = Router.from({
      case POST(p"/api/v1/short_links") => shortLinkController.postShortLinks
      case GET(p"/api/v1/short_links/$shortLinkId") => shortLinkController.getShortLink(shortLinkId)
    })

    override lazy val configuration: Configuration = ConfigurationProvider.testConfig.withFallback(context.initialConfiguration)
  }

  val originalLinkUrl = "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/"
  var newShortLinkResponse: Option[Response] = None

  "ShortLinkController#postShortLinks" should {

    "return new short link" in  {
      Given("fake request with originalLinkUrl")
      val request = FakeRequest("POST", "/api/v1/short_links")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(PostShortLinks.Request(originalLinkUrl)))

      When("postShortLinks is requested")
      val result = route(app, request).get

      Then("response has OK(200) code and application/json content ")
      status(result)                            must equal(OK)
      contentType(result)                       must contain("application/json")
      Then("response contains of ShortLink id and url ")
      contentAsJson(result).validate[Response]  mustBe a[JsSuccess[_]]
      val response = contentAsJson(result).validate[Response].get
      response.shortLinkUrl                     must startWith(components.configuration.get[String]("linkshortener.shortLink.domain"))

      newShortLinkResponse = Some(response)
    }
  }

  "ShortLinkController#getShortLink" should {

    "redirects to originalLinkUrl for known shortLinkId" in {
      Given("fake request with known shortLinkId")
      val request = FakeRequest("GET", s"/api/v1/short_links/${newShortLinkResponse.get.shortLinkId}")
        .withHeaders("Content-Type" -> "application/json")
        .withBody("")

      When("getShortLink is requested")
      val result = route(app, request).get

      Then("response redirects (303 code) to OriginalLink url")
      status(result) must equal(SEE_OTHER)
      header("Location", result) must contain(originalLinkUrl)
    }

    "returns NotFound(404) for unknown shortLinkId" in {
      Given("fake request with unknown shortLinkId")
      val unknownId = "UnknownId"
      val request = FakeRequest("GET", s"/api/v1/short_links/$unknownId")
        .withHeaders("Content-Type" -> "application/json")
        .withBody("")

      When("getShortLink is requested")
      val result = route(app, request).get

      Then("response is NotFound(404)")
      status(result) must equal(NOT_FOUND)
      header("Location", result) mustBe empty
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
