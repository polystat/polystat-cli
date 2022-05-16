package org.polystat
import cats.effect.*
import cats.syntax.all.*
import fs2.io.net.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.*
import org.http4s.implicits.*
import org.http4s.Method.GET
import fs2.text.utf8
import org.http4s.Request
import org.http4s.client.middleware.FollowRedirect

import fs2.io.file.{Path, Files}
import org.http4s.Uri
import sys.process.*
import PolystatConfig.*
import InputUtils.*

object Java:

  private val DEFAULT_J2EO_PATH = Path("j2eo.jar")
  private val J2EO_URL =
    "https://search.maven.org/remotecontent?filepath=org/polystat/j2eo/0.4.0/j2eo-0.4.0.jar"

  private def defaultJ2EO: IO[Path] =
    Files[IO]
      .exists(DEFAULT_J2EO_PATH)
      .ifM(
        ifTrue = IO.pure(DEFAULT_J2EO_PATH),
        ifFalse = downloadJ2EO.as(DEFAULT_J2EO_PATH),
      )

  private def downloadJ2EO: IO[Unit] =
    EmberClientBuilder
      .default[IO]
      .build
      .map(client => FollowRedirect(2, _ => true)(client))
      .use[Unit] { client =>
        client
          .run(
            Request[IO](
              GET,
              uri = Uri.unsafeFromString(J2EO_URL),
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
  end downloadJ2EO

  private def runJ2EO(
      j2eo: Option[Path],
      inputDir: Path,
      outputDir: Path,
  ): IO[Unit] =
    val command =
      s"java -jar ${j2eo.getOrElse(DEFAULT_J2EO_PATH)} -o $outputDir $inputDir"
    for
      j2eo <- j2eo.map(IO.pure).getOrElse(defaultJ2EO)
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

  def analyze(j2eo: Option[Path], cfg: ProcessedConfig): IO[Unit] =
    for
      tmp <- cfg.tempDir
      _ <- cfg.input match // writing EO files to tempDir
        case Input.FromStdin =>
          for
            code <- readCodeFromStdin.compile.string
            stdinTmp <- Files[IO].createTempDirectory.map(path =>
              path / "stdin.eo"
            )
            _ <- writeOutputTo(stdinTmp)(code)
            _ <- runJ2EO(j2eo, inputDir = stdinTmp, outputDir = tmp)
          yield ()
        case Input.FromFile(path) =>
          runJ2EO(j2eo, inputDir = path, outputDir = tmp)
        case Input.FromDirectory(path) =>
          runJ2EO(j2eo, inputDir = path, outputDir = tmp)
      _ <- EO.analyze(
        cfg.copy(input = Input.FromDirectory(tmp))
      )
    yield ()

end Java
