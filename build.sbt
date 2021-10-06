name := "linkshortener"
maintainer := "pawel@stepniewski.tech"
Universal / packageName := "linkshortener"
      
lazy val `linkshortener` = (project in file(".")).enablePlugins(PlayScala)
      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
      
scalaVersion := "2.13.5"

val akkaVersion = "2.6.15"
val akkaProjectionVersion = "1.2.1"
val slickVersion = "3.3.3"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.2.23",
  "org.flywaydb" %% "flyway-play" % "7.11.0",
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka"  %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.1",

  "com.lightbend.akka" %% "akka-projection-core" % akkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-jdbc" % akkaProjectionVersion,

  "org.playframework.anorm" %% "anorm" % "2.6.10",

  "com.typesafe.akka" %% "akka-persistence-testkit" % akkaVersion % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)
