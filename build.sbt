name := """deepfij9000"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test
)
libraryDependencies += "org.ccil.cowan.tagsoup" % "tagsoup" % "1.2.1"
libraryDependencies += "org.reactivemongo" %% "play2-reactivemongo" % "0.11.7.play24"
libraryDependencies += "org.webjars" % "bootstrap" % "3.3.6"
libraryDependencies += "org.webjars" % "jquery" % "1.9.1"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator