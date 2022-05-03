scalaVersion := "3.1.2"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-parse" % "0.3.7",
  "com.monovore" %% "decline" % "2.2.0",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "3.2.7",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "org.polystat.odin" %% "analysis" % "0.3.3",
  "is.cir" %% "ciris" % "2.3.2",
  "lt.dvim.ciris-hocon" %% "ciris-hocon" % "1.0.1",
).map(_.cross(CrossVersion.for3Use2_13))

excludeDependencies ++= Seq(
  "org.typelevel" % "simulacrum-scalafix-annotations_3",
  "org.typelevel" % "cats-kernel_3",
  "org.typelevel" % "cats-core_3",
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.14.1",
  "org.scalameta" %% "munit" % "1.0.0-M3" % Test,
)

assembly / assemblyJarName := "polystat.jar"

enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq(version)
buildInfoPackage := "org.polystat"

scalacOptions ++= Seq(
  "-Wunused:all"
)
