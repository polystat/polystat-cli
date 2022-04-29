scalaVersion := "3.1.2"

libraryDependencies ++= Seq(
  "com.monovore" %% "decline" % "2.2.0",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "3.2.7",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
)

val typelevelExclude = Seq(
  "cats-effect-std",
  "cats-effect",
  "simulacrum-scalafix-annotations",
  "cats-kernel",
  "cats-effect-kernel",
  "cats-core",
  "literally",
).map(p => ("org.typelevel", p + "_2.13"))

val fs2Exclude = Seq(
  ("org.scodec", "scodec-bits"),
  ("org.typelevel", "literally"),
  ("co.fs2", "fs2-core"),
  ("com.comcast", "ip4s-core"),
  ("co.fs2", "fs2-io"),
).map { case (org, p) => (org, p + "_2.13") }

val transitiveExclude = typelevelExclude ++ fs2Exclude

libraryDependencies ++= Seq(
  transitiveExclude.foldLeft(
    ("org.polystat.odin" %% "analysis" % "0.3.3")
  ) { case (acc, (org, p)) => acc.exclude(org, p) }
).map(_.cross(CrossVersion.for3Use2_13))
assembly / assemblyJarName := "polystat.jar"

scalacOptions ++= Seq(
  "-Wunused:all",
)
