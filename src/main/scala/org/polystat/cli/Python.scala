package org.polystat.cli

import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Files
import fs2.io.file.Path
import org.polystat.cli.PolystatConfig.*
import org.polystat.cli.util.InputUtils.*
import org.polystat.py2eo.transpiler.Transpile

object Python:

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    val dirForEO = cfg.tempDir / "eo"
    for
      _ <- readCodeFromInput(".py", cfg.input)
        .evalMap { case (path, code) =>
          for
            maybeCode <- IO(
              Transpile(path.filenameNoExt, code)
            )
            pathToEOCode = path
              .mount(
                to = dirForEO,
                // TODO: remove asInstanceOf
                relativelyTo = cfg.input.asInstanceOf[Input.FromDirectory].path,
              )
              .replaceExt(newExt = ".eo")
            _ <- maybeCode match
              case Some(code) =>
                writeOutputTo(pathToEOCode)(code)
              case None => IO.println(s"Couldn't analyze $path...")
          yield ()
          end for
        }
        .compile
        .drain
      _ <- EO.analyze(cfg.copy(input = Input.FromDirectory(dirForEO)))
    yield ()

end Python
