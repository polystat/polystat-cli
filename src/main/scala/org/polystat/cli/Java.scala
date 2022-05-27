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

import sys.process.*
import PolystatConfig.*
import InputUtils.*

object Java:

  private val DEFAULT_J2EO_PATH = Path("j2eo.jar")
  val DEFAULT_J2EO_VERSION = "0.5.0"
  private def j2eoUrl(j2eoVesion: String) =
    s"https://search.maven.org/remotecontent?filepath=org/polystat/j2eo/$j2eoVesion/j2eo-$j2eoVesion.jar"

  private def defaultJ2EO(j2eoVesion: String): IO[Path] =
    Files[IO]
      .exists(DEFAULT_J2EO_PATH)
      .ifM(
        ifTrue = IO.pure(DEFAULT_J2EO_PATH),
        ifFalse = downloadJ2EO(j2eoVesion),
      )

  private def downloadJ2EO(j2eoVesion: String): IO[Path] =
    EmberClientBuilder
      .default[IO]
      .build
      .map(client => FollowRedirect(2, _ => true)(client))
      .use[Unit] { client =>
        client
          .run(
            Request[IO](
              GET,
              uri = Uri.unsafeFromString(j2eoUrl(j2eoVesion)),
            )
          )
          .use(resp =>
            IO.println(
              "j2eo.jar was not found in the current working directory. Downloading..."
            ) *>
              resp.body
                .through(Files[IO].writeAll(DEFAULT_J2EO_PATH))
                .compile
                .drain
          )
      }
      .as(DEFAULT_J2EO_PATH)
  end downloadJ2EO

  private def runJ2EO(
      j2eoVersion: Option[String],
      j2eo: Option[Path],
      inputDir: Path,
      outputDir: Path,
  ): IO[Unit] =
    val command =
      s"java -jar ${j2eo.getOrElse(DEFAULT_J2EO_PATH)} -o $outputDir $inputDir"
    for
      j2eo <- j2eo
        .map(IO.pure)
        .getOrElse(defaultJ2EO(j2eoVersion.getOrElse(DEFAULT_J2EO_VERSION)))
      _ <- Files[IO]
        .exists(j2eo)
        .ifM(
          ifTrue = for
            _ <- IO.println(s"""Running "$command"...""")
            _ <- IO.blocking(command.!).void
          yield (),
          ifFalse = IO.println(s"""J2EO executable "$j2eo" doesn't exist!"""),
        )
    yield ()
    end for
  end runJ2EO

  def analyze(
      j2eoVersion: Option[String],
      j2eo: Option[Path],
      cfg: ProcessedConfig,
  ): IO[Unit] =
    for
      tmp <- cfg.tempDir
      _ <- cfg.input match // writing EO files to tempDir
        case Input.FromStdin =>
          for
            code <- readCodeFromStdin.compile.string
            stdinTmp <- Files[IO].createTempDirectory.map(path =>
              path / "stdin.java"
            )
            _ <- writeOutputTo(stdinTmp)(code)
            _ <- runJ2EO(
              j2eoVersion,
              j2eo,
              inputDir = stdinTmp,
              outputDir = tmp,
            )
          yield ()
        case Input.FromFile(path) =>
          runJ2EO(j2eoVersion, j2eo, inputDir = path, outputDir = tmp)
        case Input.FromDirectory(path) =>
          runJ2EO(j2eoVersion, j2eo, inputDir = path, outputDir = tmp)
      // J2EO deletes the tmp directory when there are no files to analyze
      // This causes the subsequent call to EO.analyze to fail, because there is no temp directory.
      // The line below patches this issue by creating the temp directory if it was deleted by J2EO.
      _ <- Files[IO] 
        .exists(tmp)
        .ifM(
          ifTrue = IO.unit,
          ifFalse = Files[IO].createDirectory(tmp),
        )
      _ <- EO.analyze(
        cfg.copy(input = Input.FromDirectory(tmp))
      )
    yield ()

end Java
