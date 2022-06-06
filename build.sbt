import ReleaseTransformations.*

ThisBuild / scalaVersion := "3.1.2"
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / releaseVersionBump := sbtrelease.Version.Bump.Next

excludeDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.13"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-parse" % "0.3.7",
  "com.monovore" %% "decline-effect" % "2.2.0",
  "co.fs2" %% "fs2-io" % "3.2.7",
  "org.polystat.odin" %% "analysis" % "0.4.2-SNAPSHOT",
  "is.cir" %% "ciris" % "2.3.2",
  "lt.dvim.ciris-hocon" %% "ciris-hocon" % "1.0.1",
  "org.http4s" %% "http4s-ember-client" % "1.0.0-M32",
  "org.scalameta" %% "munit" % "1.0.0-M3" % Test,
  "io.circe" %% "circe-core" % "0.14.1",
  "org.polystat.py2eo" % "transpiler" % "0.0.10",
  "org.slf4j" % "slf4j-nop" % "1.7.36",
)

assembly / assemblyJarName := "polystat.jar"
assembly / mainClass := (Compile / mainClass).value

enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq(version)
buildInfoPackage := "org.polystat.cli"

Global / excludeLintKeys += nativeImageVersion

Compile / mainClass := Some("org.polystat.cli.Main")

enablePlugins(NativeImagePlugin)
nativeImageVersion := "22.1.0"
nativeImageAgentOutputDir := baseDirectory.value / "native-image-configs"
nativeImageGraalHome := java.nio.file.Paths.get(sys.env.get("GRAALVM_HOME").get)
nativeImageAgentMerge := true
nativeImageOptions ++= Seq(
  s"-H:ReflectionConfigurationFiles=${baseDirectory.value / "native-image-configs" / "reflect-config.json"}",
  s"-H:ConfigurationFileDirectories=${baseDirectory.value / "native-image-configs"}",
  "-H:+ReportExceptionStackTraces",
  "-H:+JNI",
  "--initialize-at-run-time=scala.tools.nsc.ast.parser.ParsersCommon",
  "--static",
  "--no-fallback",
  "--enable-http",
  "--enable-https",
  "--verbose",
)

scalacOptions ++= Seq(
  "-Wunused:all",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:implicitConversions",
)

commands += Command.single("preRelease") { (state, nextVersion) =>
  val newState = Project
    .extract(state)
    .appendWithSession(
      Seq(
        releaseProcess := Seq(
          checkSnapshotDependencies,
          inquireVersions,
          runClean,
          runTest,
          setReleaseVersion,
          releaseStepTask(assembly),
          commitReleaseVersion,
          tagRelease,
          pushChanges,
        )
      ),
      state,
    )

  if (nextVersion == "next")
    Command.process("release with-defaults", newState)
  else
    Command.process(
      s"release with-defaults release-version $nextVersion",
      newState,
    )

}

commands += Command.command("postRelease") { state =>
  val newState = Project
    .extract(state)
    .appendWithSession(
      Seq(
        releaseProcess := Seq(
          inquireVersions,
          setNextVersion,
          commitNextVersion,
          pushChanges,
        )
      ),
      state,
    )

  Command.process("release with-defaults", newState)
}

val githubWorkflowScalas = List("3.1.2")

val checkoutSetupJava = List(WorkflowStep.Checkout) ++
  WorkflowStep.SetupJava(List(JavaSpec.temurin("11")))

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    id = "scalafmt",
    name = "Format code with scalafmt",
    scalas = githubWorkflowScalas,
    steps = checkoutSetupJava ++
      githubWorkflowGeneratedCacheSteps.value ++
      List(
        WorkflowStep.Sbt(List("scalafmtCheckAll")),
        WorkflowStep.Sbt(List("scalafmtSbtCheck")),
      ),
  )
)
