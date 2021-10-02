import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import model.shortLink.ShortLink
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

class ShortLinkSpec extends ScalaTestWithActorTestKit(ConfigurationProvider.testConfig.underlying) with AnyWordSpecLike with BeforeAndAfterEach with GivenWhenThen {

  private val shortLinkId = "testId"
  private val userAgent = "Mozilla/5.0 (platform; rv:geckoversion) Gecko/geckotrail Firefox/firefoxversion"
  private val xForwardedFor = "203.0.113.195, 70.41.3.18, 150.172.238.178"
  private val refferer = "https://example.com/"
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[ShortLink.Command, ShortLink.Event, ShortLink.Snapshot](
    system, ShortLink(shortLinkId, ConfigurationProvider.testConfig.underlying)
  )

  private val shortLinkId2 = "testId-2"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.persistenceTestKit.clearAll()
  }

  private val shortLinkDomain = ConfigurationProvider.testConfig.get[String]("linkshortener.shortLink.domain")
  private val originalLinkUrl = "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/"
  private val originalLinkTags = Set("test", "v1")

  "ShortLink#Create" should {

    "create new non-empty ShortLink" in {
      Given("empty ShortLink actor")
      // do nothing

      When("Create message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, originalLinkTags, ref))

      Then("actor replies with ShortLink in given domain and with given id")
      result.reply mustBe a[ShortLink.Commands.Create.Results.Created]
      val reply = result.reply.asInstanceOf[ShortLink.Commands.Create.Results.Created]
      reply.shortLinkId       mustEqual shortLinkId
      reply.shortLinkUrl      must      startWith(shortLinkDomain)
      Then("actor persists Created event contains of OriginalLink url and ShortLink id, domain, url")
      result.event  mustBe a[ShortLink.Events.Created]
      val event = result.event.asInstanceOf[ShortLink.Events.Created];
      event.originalLinkUrl   mustEqual originalLinkUrl
      event.shortLinkId       mustEqual reply.shortLinkId
      event.shortLinkUrl      mustEqual reply.shortLinkUrl
      event.shortLinkDomain   mustEqual shortLinkDomain
      event.tags              must      contain("test")
      event.tags              must      contain("v1")
    }

    "reply with AlreadyExists if ShortLink with given id already exists" in {
      Given("non-empty ShortLink actor")
      eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, originalLinkTags, ref))

      When("Create message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, originalLinkTags, ref))

      Then("actor replies with AlreadyExists message")
      result.reply  mustBe theSameInstanceAs(ShortLink.Commands.Create.Results.AlreadyExists)
      Then("actor persisted single Created event (effect of first Create command from 'Given' block) ")
      result.events mustBe empty
    }
  }

  "ShortLink#Click" should {

    "reply with RedirectTo OriginalLink url" in {
      Given("non-empty ShortLink actor")
      eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, originalLinkTags, ref))
      Given("User-Agent and X-Forwarded-For non empty values")
      //do nothing - values are defined above

      When("Click message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Click(Some(userAgent), Some(xForwardedFor), Some(refferer), ref))

      Then("actor replies with OriginalLink url")
      result.reply mustBe a[ShortLink.Commands.Click.Results.RedirectTo]
      val reply = result.reply.asInstanceOf[ShortLink.Commands.Click.Results.RedirectTo]
      reply.originalLinkUrl   mustEqual originalLinkUrl
      Then("actor persists Clicked event")
      result.event  mustBe a[ShortLink.Events.Clicked]
      val event = result.event.asInstanceOf[ShortLink.Events.Clicked];
      event.shortLinkId         mustEqual shortLinkId
      event.userAgentHeader     mustBe    Some(userAgent)
      event.xForwardedForHeader mustBe    Some(xForwardedFor)
      event.refererHeader       mustBe    Some(refferer)
    }

    /*
      For this test EventSourcedTestKit#runCommand cannot be used.
      ShortLink actor stops after processing Create command if it already receives Create command. EventSourcedTestKit#runCommand
      sends internal GetState message to actor after sending argument command. It results with timeout because
      actor has already been stopped.
     */
    "reply with NotFound if empty ShortLink is asked" in {
      Given("empty ShortLink actor")
      val shortLink2 = spawn(ShortLink(shortLinkId2, ConfigurationProvider.testConfig.underlying))
      //do nothing - defined above (val shortLink2)

      Given("empty User-Agent and X-Forwarder-For values")
      //do nothing

      When("Click message is send")
      val probe = createTestProbe[ShortLink.Commands.Click.Result]()
      shortLink2 ! ShortLink.Commands.Click(None, None, None, probe.ref)

      Then("actor replies with NotFound message")
      probe.expectMessage(ShortLink.Commands.Click.Results.NotFound)
      Then("actor doesn't persist any event")
      eventSourcedTestKit.persistenceTestKit.expectNothingPersisted(ShortLink.persistenceId(shortLinkId2).id)
    }
  }
}
