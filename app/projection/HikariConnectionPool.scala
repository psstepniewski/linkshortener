package projection

import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.sql.Connection
import javax.inject.{Inject, Singleton}

@Singleton
class HikariConnectionPool @Inject()(config: Config){

  private val hConfig = new HikariConfig
  private val poolSize = config.getInt("akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size")

  hConfig.setDriverClassName(config.getString("linkshortener.db.driver"))
  hConfig.setJdbcUrl(config.getString("linkshortener.db.url"))
  hConfig.setUsername(config.getString("linkshortener.db.username"))
  hConfig.setPassword(config.getString("linkshortener.db.password"))
  hConfig.setAutoCommit(false)
  hConfig.setMinimumIdle(poolSize)
  hConfig.setMaximumPoolSize(poolSize)

  private val hDataSource = new HikariDataSource(hConfig)

  def getConnection: Connection = hDataSource.getConnection
}
