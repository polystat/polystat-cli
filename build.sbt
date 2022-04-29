scalaVersion := "3.1.2"

libraryDependencies ++= Seq(
  "com.monovore" %% "decline" % "2.2.0",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "3.2.7",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "org.polystat.odin" %% "analysis" % "0.3.3",
).map(_.cross(CrossVersion.for3Use2_13))

excludeDependencies ++= Seq(
  "org.typelevel" % "simulacrum-scalafix-annotations_3",
  "org.typelevel" % "cats-kernel_3",
  "org.typelevel" % "cats-core_3",
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
)
assembly / assemblyJarName := "polystat.jar"

scalacOptions ++= Seq(
  "-Wunused:all"
)
