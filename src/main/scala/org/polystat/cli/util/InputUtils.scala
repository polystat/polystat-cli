package org.polystat.cli.util

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.stdinUtf8
import fs2.text.utf8
import org.polystat.cli.HoconConfig
import org.polystat.cli.PolystatConfig.Input
import org.polystat.cli.PolystatConfig.PolystatUsage
import FileTypes.*

import java.io.FileNotFoundException
object InputUtils:

  extension (path: Path)
    def createDirIfDoesntExist: IO[Directory] =
      Directory.fromPath(path).flatMap {
        case Some(dir) => IO.pure(dir)
        case None =>
          Files[IO].createDirectories(path).as(path.unsafeToDirectory)
      }

    def createFileIfDoesntExist: IO[File] =
      File.fromPath(path).flatMap {
        case Some(file) => IO.pure(file)
        case None =>
          path.parent match
            case Some(parent) =>
              (Files[IO].createDirectories(parent) *> Files[IO]
                .createFile(path))
                .as(path.unsafeToFile)
            case None => Files[IO].createFile(path).as(path.unsafeToFile)
      }

    def toInput: IO[Input] =
      Directory.fromPath(path).flatMap {
        case Some(dir) => IO.pure(Input.FromDirectory(dir))
        case None =>
          File.fromPath(path).flatMap {
            case Some(file) => IO.pure(Input.FromFile(file))
            case None =>
              IO.raiseError(
                new FileNotFoundException(
                  s"\"$path\" is neither a file, nor a directory!"
                )
              )
          }
      }

    def unsafeToDirectory: Directory = Directory.fromPathUnsafe(path)
    def unsafeToFile: File = File.fromPathUnsafe(path)
  end extension

  def readCodeFromFile(ext: String, file: File): Stream[IO, (File, String)] =
    Stream
      .emit(file)
      .filter(_.extName.endsWith(ext))
      .flatMap(path =>
        Files[IO]
          .readAll(path)
          .through(utf8.decode)
          .map(code => (path, code))
      )

  def readCodeFromDir(ext: String, dir: Directory): Stream[IO, (File, String)] =
    Files[IO]
      .walk(dir)
      .evalMapFilter(path =>
        File.fromPath(path).map(_.filter(_.extName.endsWith(ext)))
      )
      .flatMap(path =>
        Files[IO]
          .readAll(path)
          .through(utf8.decode)
          .map(code => (path, code))
      )

  def readCodeFromStdin: Stream[IO, String] = stdinUtf8[IO](4096).bufferAll

  def readConfigFromFile(path: File): IO[PolystatUsage.Analyze] =
    HoconConfig(path).config.load

  def writeOutputTo(path: Path)(output: String): IO[Unit] =
    for
      _ <- path.parent
        .map(Files[IO].createDirectories)
        .getOrElse(IO.unit)
      _ <- Stream
        .emits(output.getBytes)
        .through(Files[IO].writeAll(path))
        .compile
        .drain
    yield ()
    end for
  end writeOutputTo

end InputUtils
