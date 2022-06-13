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

object Java:

  private def j2eoPath(using j2eoVersion: String) = Path(
    s"j2eo-v$j2eoVersion.jar"
  )
  val DEFAULT_J2EO_VERSION = BuildInfo.j2eoVersion
  private def j2eoUrl(using j2eoVesion: String) =
    s"https://search.maven.org/remotecontent?filepath=org/polystat/j2eo/$j2eoVesion/j2eo-$j2eoVesion.jar"

  private def defaultJ2EO(using j2eoVesion: String): IO[Path] =
    Files[IO]
      .exists(j2eoPath)
      .ifM(
        ifTrue = IO.pure(j2eoPath),
        ifFalse = downloadJ2EO,
      )

  private def downloadJ2EO(using j2eoVesion: String): IO[Path] =
    EmberClientBuilder
      .default[IO]
      .build
      .map(client => FollowRedirect(2, _ => true)(client))
      .use[Unit] { client =>
        client
          .run(
            Request[IO](
              GET,
              uri = Uri.unsafeFromString(j2eoUrl),
            )
          )
          .use(resp =>
            IO.println(
              s"$j2eoPath was not found in the current working directory. Downloading..."
            ) *>
              resp.body
                .through(Files[IO].writeAll(j2eoPath))
                .compile
                .drain
          )
      }
      .as(j2eoPath)
  end downloadJ2EO

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
      dirForEO <- (cfg.tempDir / "eo").unsafeToDirectory.createDirIfDoesntExist
      _ <- runJ2EO(j2eoVersion, j2eo, input = cfg.input, outputDir = dirForEO)
      // J2EO deletes the tmp directory when there are no files to analyze
      // This causes the subsequent call to EO.analyze to fail, because there is no temp directory.
      // The line below patches this issue by creating the temp directory if it was deleted by J2EO.
      _ <- Files[IO]
        .exists(cfg.tempDir)
        .ifM(
          ifTrue = IO.unit,
          ifFalse = Files[IO].createDirectories(dirForEO),
        )
      _ <- EO.analyze(
        cfg.copy(input = dirForEO)
      )
    yield ()

end Java
