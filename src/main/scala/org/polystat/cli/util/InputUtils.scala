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

    def mount(to: Directory, relativelyTo: Directory): Path =
      val relativePath =
        relativelyTo.toPath.absolute.normalize
          .relativize(path.absolute.normalize)
      to.toPath.normalize / relativePath

    def unsafeToDirectory: Directory = Directory.fromPathUnsafe(path)
    def unsafeToFile: File = File.fromPathUnsafe(path)
  end extension

  def readCodeFromFile(ext: String, file: File): Stream[IO, (File, String)] =
    Stream
      .emit(file)
      .filter(_.extName.endsWith(ext))
      .flatMap(file =>
        Files[IO]
          .readAll(file.toPath)
          .through(utf8.decode)
          .map(code => (file, code))
      )

  def readCodeFromDir(ext: String, dir: Directory): Stream[IO, (File, String)] =
    Files[IO]
      .walk(dir.toPath)
      .evalMapFilter(path =>
        File.fromPath(path).map(_.filter(_.extName.endsWith(ext)))
      )
      .flatMap(file =>
        Files[IO]
          .readAll(file.toPath)
          .through(utf8.decode)
          .map(code => (file, code))
      )

  def readCodeFromStdin: Stream[IO, String] = stdinUtf8[IO](4096).bufferAll

  def readConfigFromFile(file: File): IO[PolystatUsage.Analyze] =
    HoconConfig(file.toPath).config.load

  def writeOutputTo(file: File)(output: String): IO[Unit] =
    Stream
      .emits(output.getBytes)
      .through(Files[IO].writeAll(file.toPath))
      .compile
      .drain
  end writeOutputTo

end InputUtils
