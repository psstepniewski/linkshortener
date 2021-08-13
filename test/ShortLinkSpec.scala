import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.{Config, ConfigFactory}
import model.shortLink.ShortLink
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

import java.io.File

object ShortLinkSpec {
  val testConfig: Config = EventSourcedBehaviorTestKit.config
    .withFallback(ConfigFactory.parseFile(new File("conf/test/0-akka-serialization.conf")).resolve())
    .withFallback(ConfigFactory.parseString("""linkshortener.shortLink.domain = "https://stepniewski.tech/f" """))
}

class ShortLinkSpec extends ScalaTestWithActorTestKit(ShortLinkSpec.testConfig) with AnyWordSpecLike with BeforeAndAfterEach with GivenWhenThen {

  private val shortLinkId = "testId"
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[ShortLink.Command, ShortLink.Event, ShortLink.State](
    system, ShortLink(shortLinkId, ShortLinkSpec.testConfig)
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  private val originalLinkUrl = "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/"

  "ShortLink" should {

    "be created with given shortLinkId" in {
      Given("shortLinkId and empty ShortLink")
      // do nothing

      When("create message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, ref))

      Then("it replies with given shortLinkId")
      result.reply mustBe a[ShortLink.Commands.Create.Results.Created]
      result.reply.asInstanceOf[ShortLink.Commands.Create.Results.Created].shortLinkId mustEqual shortLinkId
    }
  }

}
