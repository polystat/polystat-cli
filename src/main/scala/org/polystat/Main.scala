package org.polystat

import cats.effect.{IO, IOApp, ExitCode}
import cats.syntax.apply._
import cats.syntax.foldable._
import com.monovore.decline.{Opts, Command}
import com.monovore.decline.effect.CommandIOApp
import fs2.io.file.{Files, Path}
import fs2.io.{stdinUtf8}
import fs2.text.utf8
import fs2.Stream

def analyze(
    files: Stream[IO, String],
    tmp: Path,
    sarif: Boolean,
    include: List[String],
    exclude: List[String]
): IO[Unit] =
  for
    _ <- Stream
      .emits("aboba kek".getBytes)
      .through(Files[IO].writeAll(tmp / "aboba.txt"))
      .compile
      .drain
    _ <- IO.println(s"Sarif: $sarif")
    _ <- files.evalMap(IO.println).compile.drain
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
      confargs <- optsFromConfig
      _ <- IO.println(confargs)
      code <- CommandIOApp.run[IO](polystat, confargs ++ args)
    yield code

}
