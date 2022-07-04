package org.polystat.cli
import cats.effect.*
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.net.*
import fs2.text.utf8
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.middleware.FollowRedirect
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.*
import org.http4s.implicits.*
import org.polystat.cli.BuildInfo
import org.polystat.cli.util.InputUtils.*

import sys.process.*
import PolystatConfig.*
import org.polystat.cli.util.FileTypes.*
import coursier.`package`.{Organization, Module, ModuleName}
import coursier.{Fetch, Dependency}
import coursier.cache.FileCache

object Java:
  private def j2eoPath(using j2eoVersion: String) = Path(
    s"https/repo1.maven.org/maven2/org/polystat/j2eo/${j2eoVersion}/j2eo-${j2eoVersion}.jar"
  )
  val DEFAULT_J2EO_VERSION = BuildInfo.j2eoVersion
  private def j2eoUrl(using j2eoVersion: String) =
    s"https://search.maven.org/remotecontent?filepath=org/polystat/j2eo/$j2eoVersion/j2eo-$j2eoVersion.jar"

  private def defaultJ2EO(using j2eoVersion: String): IO[Path] =
    Files[IO]
      .exists(j2eoPath)
      .ifM(
        ifTrue = IO.pure(j2eoPath),
        ifFalse = downloadJ2EO,
      )

  private def downloadJ2EO(using j2eoVersion: String): IO[Path] =
    val localCache = FileCache().withLocation(java.io.File("."))
    IO.delay(
      Fetch(localCache)
        .addDependencies(
          Dependency(
            Module(
              organization = Organization("org.polystat"),
              name = ModuleName("j2eo"),
            ),
            version = j2eoVersion,
          )
        )
        .run()
    ).as(j2eoPath)

  private def runJ2EO(
      j2eoVersion: Option[String],
      j2eo: Option[File],
      input: Directory | File,
      outputDir: Directory,
  ): IO[Unit] =
    given inferredJ2eoVersion: String =
      j2eoVersion.getOrElse(DEFAULT_J2EO_VERSION)
    val inferredJ2eoPath = j2eo.getOrElse(j2eoPath)
    val command =
      s"java -jar ${inferredJ2eoPath} -o $outputDir $input"
    for
      j2eo <- j2eo
        .map(IO.pure)
        .getOrElse(defaultJ2EO)
      _ <- (for
        _ <- IO.println(s"""Running "$command"...""")
        _ <- IO.blocking(command.!).void
      yield ())
    yield ()
    end for
  end runJ2EO

  def analyze(
      j2eoVersion: Option[String],
      j2eo: Option[File],
      cfg: ProcessedConfig,
  ): IO[Unit] =
    for
      dirForEO <- (cfg.tempDir / "eo").createDirIfDoesntExist
      _ <- runJ2EO(j2eoVersion, j2eo, input = cfg.input, outputDir = dirForEO)
      // J2EO deletes the tmp directory when there are no files to analyze
      // This causes the subsequent call to EO.analyze to fail, because there is no temp directory.
      // The line below patches this issue by creating the temp directory if it was deleted by J2EO.
      _ <- cfg.tempDir.toPath.createDirIfDoesntExist
      _ <- EO.analyze(
        cfg.copy(input = dirForEO)
      )
    yield ()

end Java
