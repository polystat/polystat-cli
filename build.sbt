scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "com.monovore" %% "decline" % "2.2.0",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "3.2.7",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "org.me" %% "odin" % "0.3.4-SNAPSHOT",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
)

assembly / assemblyJarName := "polyscat.jar"

scalacOptions ++= Seq(
  "-Ymacro-annotations",
  "-Wunused",
)
