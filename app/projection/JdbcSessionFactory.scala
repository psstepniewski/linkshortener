package projection

import akka.japi.function
import projection.JdbcSessionFactory.JdbcSession

import java.sql.Connection
import javax.inject.{Inject, Singleton}


@Singleton
class JdbcSessionFactory @Inject()(projectionDataSource: HikariConnectionPool){
  def create() = new JdbcSession(projectionDataSource.getConnection)
}

object JdbcSessionFactory {

  class JdbcSession(connection: Connection) extends akka.projection.jdbc.JdbcSession {
    override def withConnection[Result](func: function.Function[Connection, Result]): Result = func(connection)
    override def commit(): Unit = connection.commit()
    override def rollback(): Unit = connection.rollback()
    override def close(): Unit = connection.close()
  }
}
