name := """flimey-core"""

organization := "io.flimey"
version := "0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-slick" % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.0.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % "test",

  "org.postgresql" % "postgresql" % "42.2.23",

  "org.mindrot" % "jbcrypt" % "0.4"

)
