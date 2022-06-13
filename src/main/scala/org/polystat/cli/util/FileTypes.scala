package org.polystat.cli.util

import fs2.io.file.Path
import cats.effect.IO
import fs2.io.file.Files

object FileTypes:
  class Directory private[FileTypes] (
      private[FileTypes] val underlying: Path
  ):
    override def toString = underlying.toString

  given Conversion[Directory, Path] = _.underlying
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
        case None => IO.raiseError(new Exception(s"$path must be a directory!"))
      }

    def fromPathUnsafe(path: Path): Directory = Directory(path)

  extension (dir: Directory)
    def createDirIfDoesntExist: IO[Directory] =
      Files[IO]
        .exists(dir)
        .ifM(
          ifTrue = IO.pure(dir),
          ifFalse = Files[IO].createDirectories(dir).as(dir),
        )

    def clean: IO[Directory] =
      for
        _ <- Files[IO].deleteRecursively(dir)
        _ <- Files[IO].createDirectory(dir)
      yield dir

  class File private[FileTypes] (private[FileTypes] val underlying: Path):
    override def toString(): String = underlying.toString

  given Conversion[File, Path] = _.underlying

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
          IO.raiseError(new Exception(s"$path must be a regular file!"))
      }

    def fromPathUnsafe(path: Path): File = File(path)

  extension (file: File)
    def createFileIfDoesntExist: IO[File] =
      file.parent match
        case Some(parent) =>
          (Files[IO].createDirectories(parent) *> Files[IO].createFile(file))
            .as(file)
        case None => Files[IO].createFile(file).as(file)

    def filenameNoExt: String =
      val fileName = file.fileName.toString
      fileName.splitAt(fileName.indexOf("."))._1

    def replaceExt(newExt: String): File =
      File.fromPathUnsafe(
        Path(
          file.toString
            .splitAt(file.toString.lastIndexOf("."))
            ._1 + newExt
        )
      )

    def mount(to: Directory, relativelyTo: Directory): File =
      val relativePath =
        relativelyTo.absolute.normalize.relativize(file.absolute.normalize)
      File.fromPathUnsafe(to.normalize / relativePath)
