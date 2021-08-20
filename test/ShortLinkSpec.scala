import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import model.shortLink.ShortLink
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

class ShortLinkSpec extends ScalaTestWithActorTestKit(ConfigurationProvider.testConfig.underlying) with AnyWordSpecLike with BeforeAndAfterEach with GivenWhenThen {

  private val shortLinkId = "testId"
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[ShortLink.Command, ShortLink.Event, ShortLink.State](
    system, ShortLink(shortLinkId, ConfigurationProvider.testConfig.underlying)
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  private val shortLinkDomain = ConfigurationProvider.testConfig.get[String]("linkshortener.shortLink.domain")
  private val originalLinkUrl = "https://stepniewski.tech/blog/post/4-linkshortener-with-akka-concept/"

  "ShortLink#Create" should {

    "create new non-empty ShortLink" in {
      Given("empty ShortLink actor")
      // do nothing

      When("Create message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, ref))

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
    }

    "reply with AlreadyExists if ShortLink with given id already exists" in {
      Given("non-empty ShortLink actor")
      eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, ref))

      When("Create message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, ref))

      Then("actor replies with AlreadyExists message")
      result.reply mustBe theSameInstanceAs(ShortLink.Commands.Create.Results.AlreadyExists)
      Then("actor doesn't persist any event")
      result.events mustBe empty
    }
  }

  "ShortLink#Click" should {

    "reply with RedirectTo OriginalLink url" in {
      Given("non-empty ShortLink actor")
      eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Create(originalLinkUrl, ref))

      When("Click message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Click(ref))

      Then("actor replies with OriginalLink url")
      result.reply mustBe a[ShortLink.Commands.Click.Results.RedirectTo]
      val reply = result.reply.asInstanceOf[ShortLink.Commands.Click.Results.RedirectTo]
      reply.originalLinkUrl   mustEqual originalLinkUrl
      Then("actor persists LinkClicked event")
      result.event  mustBe a[ShortLink.Events.LinkClicked]
      val event = result.event.asInstanceOf[ShortLink.Events.LinkClicked];
      event.shortLinkId       mustEqual shortLinkId
    }

    "reply with NotFound if empty ShortLink is asked" in {
      Given("empty ShortLink actor")
      // do nothing

      When("Click message is send")
      val result = eventSourcedTestKit.runCommand(ref => ShortLink.Commands.Click(ref))

      Then("actor replies with NotFound message")
      result.reply mustBe theSameInstanceAs(ShortLink.Commands.Click.Results.NotFound)
      Then("actor doesn't persist any event")
      result.events mustBe empty
    }
  }
}
