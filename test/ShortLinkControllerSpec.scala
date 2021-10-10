import ShortLinkControllerSpec.PostShortLinks.{Response, requestWrites}
import ShortLinkControllerSpec.{PostShortLinks, ReturnGivenId}
import akka.util.Timeout
import controllers.ShortLinkController
import model.shortLink.ShortLinkSharding
import model.{Base58IdGenerator, IdGenerator}
import org.scalatest.GivenWhenThen
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, SEE_OTHER}
import play.api.libs.json.{JsSuccess, Json, Reads, Writes}
import play.api.routing.Router
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsJson, contentType, header, route, status}
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents}

import scala.concurrent.duration._

class ShortLinkControllerSpec extends PlaySpec with OneAppPerSuiteWithComponents with GivenWhenThen {

  implicit val timeout: Timeout = 5.seconds

//  Don't use `components` in test body (it will cause `Address already in use` error), try to use `app` reference instead (for example `app.configuration`).
//  Every call of 'components' starts new application instance.
  override def components: BuiltInComponents =  new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

    private val shortLinkSharding = new ShortLinkSharding(actorSystem, configuration.underlying)
    val shortLinkController = new ShortLinkController(new Base58IdGenerator(), shortLinkSharding, controllerComponents, actorSystem)(executionContext)
    val shortLinkControllerWithSameId = new ShortLinkController(new ReturnGivenId("testId"), shortLinkSharding, controllerComponents, actorSystem)(executionContext)
    import play.api.routing.sird._

    lazy val router: Router = Router.from({
      case POST(p"/api/v1/short_links")              => shortLinkController.postShortLinks
      case POST(p"/api/v1/short_links_same_id")      => shortLinkControllerWithSameId.postShortLinks
      case GET (p"/api/v1/short_links/$shortLinkId") => shortLinkController.getShortLink(shortLinkId)
    })

    override lazy val configuration: Configuration = ConfigurationProvider.testConfig.withFallback(context.initialConfiguration)
  }

  private val originalLinkUrl = "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/"
  private val originalLinkTags = Set("test", "v1")
  var newShortLinkResponse: Option[Response] = None

  "ShortLinkController#postShortLinks" should {

    "return new short link" in  {
      Given("fake request with originalLinkUrl")
      val request = FakeRequest("POST", "/api/v1/short_links")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(PostShortLinks.Request(originalLinkUrl, originalLinkTags)))

      When("postShortLinks is requested")
      val result = route(app, request).get

      Then("response has OK(200) code and application/json content ")
      status(result)                            must equal(OK)
      contentType(result)                       must contain("application/json")
      Then("response contains of ShortLink id and url ")
      contentAsJson(result).validate[Response]  mustBe a[JsSuccess[_]]
      val response = contentAsJson(result).validate[Response].get
      response.shortLinkUrl                     must equal(s"${app.configuration.get[String]("linkshortener.shortLink.domain")}/${response.shortLinkId}")
      newShortLinkResponse = Some(response)
    }

    "return InternalServerError(500) if controller always generate the same shortLinkId" in  {
      Given("fake request with originalLinkUrl")
      val request = FakeRequest("POST", "/api/v1/short_links_same_id")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.toJson(PostShortLinks.Request(originalLinkUrl, originalLinkTags)))
      Given("controller which always generate the same shortLinkId")
      // nothing - controller is declared above

      When("postShortLinks was already requested")
      route(app, request)
      When("postShortLinks is requested again")
      val result = route(app, request).get

      Then("response is InternalServerError(500)")
      status(result) must equal(INTERNAL_SERVER_ERROR)
    }
  }

  "ShortLinkController#getShortLink" should {

    "redirects to originalLinkUrl for known shortLinkId" in {
      Given("fake request with earlier used shortLinkId")
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
      Given("fake request with never used shortLinkId")
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
    case class Request(originalLinkUrl: String, tags: Set[String])
    implicit val requestWrites: Writes[Request] = Json.writes[Request]

    case class Response(shortLinkId: String, shortLinkUrl: String)
    implicit val responseReads: Reads[Response] = Json.reads[Response]
  }

  class ReturnGivenId(id: String) extends IdGenerator {
    override def newId: String = id
  }
}
