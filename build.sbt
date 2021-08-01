name := "linkshortener"
 
version := "1.0" 
      
lazy val `linkshortener` = (project in file(".")).enablePlugins(PlayScala)

      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
      
scalaVersion := "2.13.5"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.23",
  "org.flywaydb" %% "flyway-play" % "7.11.0"
)
