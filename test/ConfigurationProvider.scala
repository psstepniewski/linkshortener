import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import play.api.Configuration

object ConfigurationProvider {

  val testConfig: Configuration = Configuration (
    EventSourcedBehaviorTestKit.config
    .withFallback(ConfigFactory.parseString(s"""linkshortener.shortLink.domain = "https://stepniewski.tech/f" """))
    .withFallback(ConfigFactory.parseString(
      s"""
         |akka {
         |  actor {
         |    serializers {
         |      jackson-cbor = "akka.serialization.jackson.JacksonCborSerializer"
         |    }
         |    serialization-bindings {
         |        "model.CborSerializable" = jackson-cbor
         |    }
         |  }
         |}
         |""".stripMargin))
  )
}
