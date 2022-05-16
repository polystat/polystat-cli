package org.polystat
import cats.effect.*
import cats.syntax.all.*
import fs2.text.utf8
import fs2.io.file.{Path, Files}

// entrypoint for J2EO
import main.Main2

object J2EO:

  def run(inputDir: Path, outputDir: Path): IO[Unit] =
    val command =
      IO.blocking(
        Main2.main(Array("-o", outputDir.toString, inputDir.toString))
      )
    for
      _ <- IO.println(s"""Running "$command"...""")
      _ <- command.void
    yield ()
    end for
  end run

end J2EO
