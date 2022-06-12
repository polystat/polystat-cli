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

import java.io.FileNotFoundException
object InputUtils:

  extension (path: Path)

    def mount(to: Path, relativelyTo: Path): Path =
      val relativePath =
        relativelyTo.absolute.normalize.relativize(path.absolute.normalize)
      to.normalize / relativePath

    def filenameNoExt: String =
      val fileName = path.fileName.toString
      fileName.splitAt(fileName.indexOf("."))._1

    def createDirIfDoesntExist: IO[Path] =
      Files[IO]
        .exists(path)
        .ifM(
          ifTrue = IO.pure(path),
          ifFalse = Files[IO].createDirectories(path).as(path),
        )

    def replaceExt(newExt: String): Path =
      Path(
        path.toString
          .splitAt(path.toString.lastIndexOf("."))
          ._1 + newExt
      )

    def clean: IO[Path] =
      for
        _ <- Files[IO].deleteRecursively(path)
        _ <- Files[IO].createDirectory(path)
      yield path

    def toInput: IO[Input] =
      Files[IO]
        .isDirectory(path)
        .ifM(
          ifTrue = IO.pure(Input.FromDirectory(path)),
          ifFalse = Files[IO]
            .isFile(path.toNioPath)
            .ifM(
              ifTrue = IO.pure(Input.FromFile(path)),
              ifFalse = IO.raiseError(
                new FileNotFoundException(
                  s"\"$path\" is neither a file, nor a directory!"
                )
              ),
            ),
        )
  end extension

  def readCodeFromFile(ext: String, path: Path): Stream[IO, (Path, String)] =
    Stream
      .emit(path)
      .filter(_.extName.endsWith(ext))
      .flatMap(path =>
        Files[IO]
          .readAll(path)
          .through(utf8.decode)
          .map(code => (path, code))
      )

  def readCodeFromDir(ext: String, dir: Path): Stream[IO, (Path, String)] =
    Files[IO]
      .walk(dir)
      .filter(_.extName.endsWith(ext))
      .flatMap(path =>
        Files[IO]
          .readAll(path)
          .through(utf8.decode)
          .map(code => (path, code))
      )

  def readCodeFromStdin: Stream[IO, String] = stdinUtf8[IO](4096).bufferAll

  def readCodeFromInput(ext: String, input: Input): Stream[IO, (Path, String)] =
    input match
      case Input.FromFile(path) =>
        readCodeFromFile(ext = ext, path = path)
      case Input.FromDirectory(path) =>
        readCodeFromDir(ext = ext, dir = path)
      case Input.FromStdin =>
        readCodeFromStdin.map(code => (Path("stdin" + ext), "\n" + code + "\n"))

  def readConfigFromFile(path: Path): IO[PolystatUsage.Analyze] =
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
