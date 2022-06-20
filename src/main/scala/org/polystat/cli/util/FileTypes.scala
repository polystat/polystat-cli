package org.polystat.cli.util

import fs2.io.file.Path
import cats.effect.IO
import fs2.io.file.Files
import java.nio.file.Path as JPath

object FileTypes:
  final class Directory private[FileTypes] (
      private[FileTypes] val underlying: Path
  ):
    override def toString = underlying.toString
    def toPath: Path = underlying
    def toNioPath: JPath = underlying.toNioPath
    def /(s: String): Path = underlying / s
    def clean: IO[Directory] =
      for
        _ <- Files[IO].deleteRecursively(underlying)
        _ <- Files[IO].createDirectory(underlying)
      yield this

  object Directory:

    def fromPath(path: Path): IO[Option[Directory]] =
      Files[IO]
        .isDirectory(path)
        .ifM(
          ifTrue = IO.pure(Some(Directory(path))),
          ifFalse = IO.pure(None),
        )

    def fromPathFailFast(path: Path): IO[Directory] =
      fromPath(path).flatMap {
        case Some(dir) => IO.pure(dir)
        case None =>
          IO.raiseError(
            new Exception(s"$path is either not a directory or does not exist!")
          )
      }

    def createTempDirectory: IO[Directory] =
      Files[IO].createTempDirectory.map(Directory(_))

    def fromPathUnsafe(path: Path): Directory = Directory(path)

  final class File private[FileTypes] (private[FileTypes] val underlying: Path):
    override def toString(): String = underlying.toString
    def toPath = underlying
    def toNioPath: JPath = underlying.toNioPath
    def extName = underlying.extName
    def filenameNoExt: String =
      val fileName = underlying.fileName.toString
      fileName.splitAt(fileName.indexOf("."))._1

    def replaceExt(newExt: String): File =
      File.fromPathUnsafe(
        Path(
          underlying.toString
            .splitAt(underlying.toString.lastIndexOf("."))
            ._1 + newExt
        )
      )

  object File:
    def fromPath(path: Path): IO[Option[File]] =
      Files[IO]
        .isRegularFile(path)
        .ifM(
          ifTrue = IO.pure(Some(File(path))),
          ifFalse = IO.pure(None),
        )

    def fromPathFailFast(path: Path): IO[File] =
      fromPath(path).flatMap {
        case Some(file) => IO.pure(file)
        case None =>
          IO.raiseError(
            new Exception(
              s"$path is either not a regular file or does not exist!"
            )
          )
      }

    def fromPathUnsafe(path: Path): File = File(path)
