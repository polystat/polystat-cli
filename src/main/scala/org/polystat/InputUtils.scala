package org.polystat
import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.stdinUtf8
import fs2.text.utf8

import java.io.FileNotFoundException

import PolystatConfig.Input

object InputUtils:
  extension (path: Path)
    def replaceExt(newExt: String): Path =
      Path(
        path.toString
          .splitAt(path.toString.lastIndexOf("."))
          ._1 + newExt
      )

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

  def relativePath(to: Path, path: Path): Path =
    to.absolute.normalize.relativize(path.absolute.normalize)

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
          .map(code => (relativePath(to = dir, path = path), code))
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
end InputUtils
