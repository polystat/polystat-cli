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
    for
      dirForEO <- (cfg.tempDir / "eo").createDirIfDoesntExist
      _ <- readCodeFromDir(".py", cfg.input)
        .evalMap { case (file, code) =>
          for
            maybeCode <- IO(
              Transpile(file.filenameNoExt, code)
            )
            pathToEOCode <- file.toPath
              .mount(
                to = dirForEO,
                relativelyTo = cfg.input,
              )
              .replaceExtThenCreateFile(newExt = ".eo")
            _ <- maybeCode match
              case Some(code) =>
                writeOutputTo(pathToEOCode)(code)
              case None => IO.println(s"Couldn't analyze $file...")
          yield ()
          end for
        }
        .compile
        .drain
      _ <- EO.analyze(cfg.copy(input = dirForEO))
    yield ()

end Python
