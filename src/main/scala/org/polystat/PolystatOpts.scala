package org.polystat

import com.monovore.decline.Opts
import cats.effect.IO
import java.nio.file.{Path => JPath}
import fs2.io.file.{Path, Files}
import fs2.io.stdinUtf8
import com.monovore.decline.Opts
import fs2.text.utf8
import cats.syntax.foldable._
import cats.syntax.applicative._
import fs2.Stream

object PolystatOpts {

def readCodeFromStdin: Stream[IO, String] =
  stdinUtf8[IO](4096).foldMonoid

def readCodeFromFiles(dir: Path): Stream[IO, String] =
  Files[IO]
    .walk(dir)
    .flatMap(p => {
      if (p.extName == ".eo")
        Files[IO]
          .readAll(p)
          .through(utf8.decode)
      else Stream.empty
    }
    )

val include: Opts[List[String]] =
  Opts.options[String](long = "exclude", help = "Rules to exclude").orEmpty

val exclude: Opts[List[String]] =
  Opts.options[String](long = "include", help = "Rules to include").orEmpty

val sarif: Opts[Boolean] = Opts
  .flag(long = "sarif", help = "Should output be in SARIF format?")
  .orFalse

val tmp: Opts[IO[Path]] = Opts
  .option[JPath](
    long = "tmp",
    help = "The directory where temporary files should be stored.",
  )
  .map(p => Path.fromNioPath(p).pure[IO])
  .orElse(Files[IO].createTempDirectory.pure[Opts])

val files: Opts[Stream[IO, String]] = Opts
  .option[JPath](
    long = "files",
    help = "The directory with EO files.",
  )
  .map(p => readCodeFromFiles(Path.fromNioPath(p)))
  .orElse(readCodeFromStdin.pure[Opts])
}