name := "linkshortener"
 
version := "1.0" 
      
lazy val `linkshortener` = (project in file(".")).enablePlugins(PlayScala)

      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
      
scalaVersion := "2.13.5"

val AkkaVersion = "2.6.15"
val SlickVersion = "3.3.3"
libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.23",
  "org.flywaydb" %% "flyway-play" % "7.11.0",
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.1",

  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
)
