import akka.Done
import akka.actor.ActorSystem
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.util.Timeout
import controllers.ShortLinkController
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.routing.Router
import play.api.test.Helpers.status
import play.api.test.{FakeRequest, Helpers, Injecting}
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents}

import scala.concurrent.Future
import scala.concurrent.duration._

class ShortLinkControllerTest extends PlaySpec with OneAppPerSuiteWithComponents with Injecting {

  override def components: BuiltInComponents = new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {
    lazy val router: Router = Router.empty
    override lazy val configuration: Configuration = context.initialConfiguration
  }

  implicit val timeout: Timeout = 20.seconds
  implicit val system: ActorSystem = ActorSystem("example")
  val done: Future[Done] = SchemaUtils.createIfNotExists()

  "ShortLinkController#postShortLinks" should {
    "return 200 for requested link" in  {
      val controller  = new ShortLinkController(Helpers.stubControllerComponents(), system, components.configuration.underlying)(components.executionContext)
      val result      = controller.postShortLinks.apply(FakeRequest().withMethod("POST").withHeaders("Content-Type" -> "application/json").withBody(Json.obj("originalLinkUrl" -> "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/")))
      status(result) mustEqual OK
    }
  }
}
