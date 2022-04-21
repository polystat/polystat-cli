scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "com.monovore" %% "decline" % "2.2.0",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "3.2.7",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "org.polystat.odin" %% "analysis" % "0.3.3",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
)

scalacOptions ++= Seq(
  "-Ymacro-annotations",
  "-Wunused",
)
