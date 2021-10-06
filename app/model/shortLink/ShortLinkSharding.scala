package model.shortLink

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef, EntityTypeKey}
import com.typesafe.config.Config

import javax.inject.{Inject, Singleton}

@Singleton
class ShortLinkSharding @Inject()(actorSystem: ActorSystem, config: Config) {

  private val sharding: ClusterSharding = ClusterSharding(actorSystem.toTyped)
  private val entityTypeKey: EntityTypeKey[ShortLink.Command] = ShortLink.TypeKey

  sharding.init(Entity(entityTypeKey) { entityContext =>
    ShortLink(entityContext.entityId, config)
  })

  def entityRefFor(id: String): EntityRef[ShortLink.Command] = {
    sharding.entityRefFor(entityTypeKey, id)
  }
}
