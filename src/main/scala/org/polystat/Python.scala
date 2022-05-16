package org.polystat

import org.polystat.py2eo.transpiler.Transpile
import fs2.io.file.{Files, Path}
import cats.effect.{IO, IOApp}
import org.polystat.PolystatConfig.*
import org.polystat.InputUtils.*

object Python:

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    for
      tmp <- cfg.tempDir
      _ <- readCodeFromInput(".py", cfg.input)
        .evalMap { case (path, code) =>
          for
            maybeCode <- IO.blocking(Transpile(path.toString, code))
            _ <- maybeCode match
              case Some(code) =>
                writeOutputTo(tmp / path.replaceExt(".eo"))(code)
              case None => IO.println(s"Couldn't analyze $path...")
          yield ()
        }
        .compile
        .drain
      _ <- EO.analyze(cfg.copy(input = Input.FromDirectory(tmp)))
    yield ()

end Python
