import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

object ShardTest {

  object ShardMock {
    def apply(): Behavior[ClusterSharding.ShardCommand] = Behaviors.receive((ctx, msg) =>
      msg match {
        case m: ClusterSharding.ShardCommand =>
          ctx.log.info(s"ShardMock receives ShardCommand[$m]")
          Behaviors.same
      }
    )
  }
}
