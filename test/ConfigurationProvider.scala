import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import play.api.Configuration

object ConfigurationProvider {

  val testConfig: Configuration = Configuration (
    EventSourcedBehaviorTestKit.config
    .withFallback(ConfigFactory.parseString(
      s"""
         |linkshortener.shortLink.domain = "https://stepniewski.tech/f"
         |
         |akka {
         |  remote.artery {
         |    canonical {
         |      hostname = "127.0.0.1"
         |      port = 2555
         |    }
         |  }
         |  cluster {
         |    seed-nodes = [
         |      "akka://application@127.0.0.1:2555"
         |    ]
         |  }
         |}
         |""".stripMargin))
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
    .withFallback(ConfigFactory.parseString(
      s"""
         |akka {
         |  actor {
         |    provider = "cluster"
         |  }
         |
         |  cluster {
         |    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
         |
         |    sharding {
         |        state-store-mode = ddata
         |        remember-entities-store = ddata
         |        least-shard-allocation-strategy.rebalance-absolute-limit = 20
         |    }
         |  }
         |}
         |
         |""".stripMargin))
  )
}
