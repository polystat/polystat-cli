import ReleaseTransformations._
import Dependencies._
import scala.sys.process._

ThisBuild / scalaVersion := "3.2.0"
ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / releaseVersionBump := sbtrelease.Version.Bump.Next

homepage := Some(url("https://github.com/polystat/polystat-cli"))
licenses := Seq(License.MIT)
organizationName := "Polystat"
organization := "org.polystat"
name := "polystat-cli"
organizationHomepage := Some(url("https://www.polystat.org/)"))
developers := List(
  Developer(
    id = "nikololiahim",
    name = "Mikhail Olokin",
    email = "olomishcak@outlook.com",
    url = url("https://github.com/nikololiahim"),
  )
)

sonatypeProfileName := "org.polystat"
sonatypeCredentialHost := "s01.oss.sonatype.org"
scmInfo :=
  Some(
    ScmInfo(
      url("https://github.com/polystat/polystat-cli"),
      "scm:git@github.com:polystat/polystat-cli.git",
    )
  )
publishTo := sonatypePublishToBundle.value
publishArtifact := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
resolvers += Opts.resolver.sonatypeSnapshots

excludeDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.13"
)

libraryDependencies ++= Seq(
  "org.latestbit" %% "circe-tagged-adt-codec" % "0.11.0",
  "co.fs2" %% "fs2-io" % "3.2.12",
  "com.monovore" %% "decline-effect" % "2.3.0",
  "io.circe" %% "circe-core" % "0.15.0-M1",
  "is.cir" %% "ciris" % "2.3.3",
  "lt.dvim.ciris-hocon" %% "ciris-hocon" % "1.0.1",
  "org.http4s" %% "http4s-ember-client" % "1.0.0-M32",
  "org.polystat.odin" %% "analysis" % V.odin,
  "org.polystat.py2eo" % "transpiler" % V.py2eo,
  "org.polystat" % "far" % V.far,
  "org.scalameta" %% "munit" % "1.0.0-M6" % Test,
  "org.slf4j" % "slf4j-nop" % "1.7.36",
)

packageOptions := Seq(
  sbt.Package.ManifestAttributes(
    ("EO-Version", "https://github.com/polystat/polystat-cli/issues/18")
  )
)

assembly / assemblyJarName := "polystat.jar"
assembly / mainClass := (Compile / mainClass).value
assembly / assemblyMergeStrategy := {
  case p if p.endsWith("versions/9/module-info.class") =>
    MergeStrategy.first
  case PathList(ps @ _*) if ps.last.endsWith("log4j.properties") =>
    MergeStrategy.first
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

enablePlugins(BuildInfoPlugin)
buildInfoKeys := Seq(
  version,
  organizationHomepage,
  "farVersion" -> V.far,
  "j2eoVersion" -> V.j2eo,
  "odinVersion" -> V.odin,
  "py2eoVersion" -> V.py2eo,
  "versionSummary" ->
    s"""|far = ${V.far}
        |j2eo = ${V.j2eo}
        |odin = ${V.odin}
        |polystat-cli = ${version.value}
        |py2eo = ${V.py2eo}
        |""".stripMargin,
)
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
)

lazy val addDescriptorChanges = taskKey[Unit](
  "git add descriptor changes"
)

addDescriptorChanges := {
  "git add coursier/polystat.json".!
}

lazy val generateDescriptor =
  taskKey[Unit](
    "Generate a descriptor that is used by coursier to install polystat-cli."
  )

generateDescriptor := {
  val ver = version.value
  val org = organization.value
  val artifactName = name.value
  val descriptor =
    s"""|{
        |    "polystat": {
        |        "repositories": [
        |            "ivy2Local",
        |            "central"
        |        ],
        |        "dependencies": [
        |            "${org}:${artifactName}_3:${ver}"
        |        ]
        |    }
        |}
        |""".stripMargin
  println(descriptor)
  java.nio.file.Files
    .write(
      java.nio.file.Paths.get("coursier/polystat.json"),
      descriptor.getBytes,
    )
}

commands += Command.args("preRelease", "<arg>") { (state, args) =>
  args match {
    case Seq(nextVersion) =>
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
              releaseStepTask(generateDescriptor),
              releaseStepTask(addDescriptorChanges),
              commitReleaseVersion,
              tagRelease,
              releaseStepCommandAndRemaining("publishSigned"),
              releaseStepCommand("sonatypeBundleRelease"),
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
    case other =>
      println("wrong number of arguments passed!")
      state
  }
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

val checkoutSetupJava = List(WorkflowStep.Checkout) ++
  WorkflowStep.SetupJava(List(JavaSpec.temurin("11")))

ThisBuild / githubWorkflowPublishTargetBranches := Seq()

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    id = "scalafmt",
    name = "Format code with scalafmt",
    scalas = List(scalaVersion.value),
    steps = checkoutSetupJava ++
      githubWorkflowGeneratedCacheSteps.value ++
      List(
        WorkflowStep.Sbt(List("scalafmtCheckAll")),
        WorkflowStep.Sbt(List("scalafmtSbtCheck")),
      ),
  )
)
