package org.polystat
import fs2.Stream
import fs2.io.file.Path
import cats.effect.IO
import fs2.io.file.Files
import fs2.text.utf8
import fs2.io.stdinUtf8
import PolystatConfig.Input
import cats.data.NonEmptyList

extension (path: Path)
  def replaceExt(newExt: String): Path =
    Path(
      path.toString
        .splitAt(path.toString.lastIndexOf("."))
        ._1 + newExt
    )

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
