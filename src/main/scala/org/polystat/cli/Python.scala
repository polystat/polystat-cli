package org.polystat.cli

import cats.effect.IO
import cats.effect.IOApp
import fs2.io.file.Files
import fs2.io.file.Path
import org.polystat.cli.InputUtils.*
import org.polystat.cli.PolystatConfig.*
import org.polystat.py2eo.transpiler.Transpile

object Python:

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    for
      _ <- readCodeFromInput(".py", cfg.input)
        .evalMap { case (path, code) =>
          val fileName = path.fileName.toString
          for
            maybeCode <- IO(
              Transpile(fileName.splitAt(fileName.indexOf("."))._1, code)
            )
            _ <- maybeCode match
              case Some(code) =>
                writeOutputTo(cfg.tempDir / path.replaceExt(".eo"))(code)
              case None => IO.println(s"Couldn't analyze $path...")
          yield ()
          end for
        }
        .compile
        .drain
      _ <- EO.analyze(cfg.copy(input = Input.FromDirectory(cfg.tempDir)))
    yield ()

end Python
