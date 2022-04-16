package org.polystat

import cats.effect.*
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.foldable._
import com.monovore.decline.*
import com.monovore.decline.effect.*
import cats.Monad
import cats.data.NonEmptyList as Nel
import java.nio.file.Path as JPath
import fs2.io.file.{Files, Path}
import fs2.io.{stdinUtf8}
import cats.effect.ExitCode
import fs2.text.{utf8, lines}
import fs2.Stream

def readCodeFromStdin: IO[List[String]] =
  stdinUtf8[IO](4096).foldMonoid.compile.string.map(p => List(p))

def readCodeFromFiles(dir: Path): IO[List[String]] =
  Files[IO]
    .walk(dir)
    .flatMap(p =>
      if p.extName == ".eo" then
        Files[IO]
          .readAll(p)
          .through(utf8.decode)
      else Stream.empty
    )
    .compile
    .toList

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
    help = "The directory where temporary files should be stored."
  )
  .map(p => Path.fromNioPath(p).pure[IO])
  .orElse(Files[IO].createTempDirectory.pure[Opts])

val files: Opts[IO[List[String]]] = Opts
  .option[JPath](
    long = "files",
    help = "The directory with EO files."
  )
  .map(p => readCodeFromFiles(Path.fromNioPath(p)))
  .orElse(readCodeFromStdin.pure[Opts])

case class PolystatConfig(
    // TODO: make 'files' IO[Path] 
    tmp: IO[Path],
    files: IO[List[String]],
    sarif: Boolean,
    includes: List[String],
    excludes: List[String]
)

object PolystatConfig {
  def opts: Opts[PolystatConfig] =
    (tmp, files, sarif, include, exclude).mapN(PolystatConfig.apply)
}

def analyze(
    files: List[String],
    tmp: Path,
    sarif: Boolean,
    include: List[String],
    exclude: List[String]
): IO[Unit] =
  for
    f <- Stream
      .emits("aboba kek".getBytes)
      .through(Files[IO].writeAll(tmp / "aboba.txt"))
      .compile
      .drain
    _ <- IO.println(s"Sarif: $sarif")
    _ <- IO(files.foreach(println))
    _ <- IO.println("Include: ")
    _ <- include.traverse_(IO.println)
    _ <- IO.println("Exclude: ")
    _ <- exclude.traverse_(IO.println)
  yield ()

val polystat = Command[PolystatConfig](
  name = "polystat",
  header = "Says hello!",
  helpFlag = true
)(PolystatConfig.opts).map {
  case PolystatConfig(tmp, files, sarif, include, exclude) =>
    for {
      files <- files
      tmp <- tmp
      _ <- analyze(files, tmp, sarif, include, exclude)
    } yield ExitCode.Success
}

val optsFromConfig: IO[List[String]] = Files[IO]
  .readAll(Path(".polystat"))
  .through(utf8.decode)
  .flatMap(s => Stream.emits(s.trim.split("\\s+")))
  .filter(_.nonEmpty)
  .compile
  .toList

object Polystat extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    for
      args <- optsFromConfig
      _ <- IO.println(args)
      code <- CommandIOApp.run[IO](polystat, args)
    yield code

}
