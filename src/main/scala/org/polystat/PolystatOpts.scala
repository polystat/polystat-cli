package org.polystat

import cats.data.NonEmptyList
import cats.data.Validated.*
import cats.effect.IO
import cats.syntax.applicative.*
import cats.syntax.apply.*
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.stdinUtf8
import fs2.text.utf8

import java.nio.file.{Path as JPath}

object PolystatOpts:

  enum IncludeExclude:
    case Include(rules: NonEmptyList[String])
    case Exclude(rules: NonEmptyList[String])
    case Nothing
  end IncludeExclude

  def readCodeFromStdin: Stream[IO, String] =
    stdinUtf8[IO](4096).foldMonoid

  def readCodeFromFiles(dir: Path): Stream[IO, (Path, String)] =
    Files[IO]
      .walk(dir)
      .evalMapFilter(p =>
        if p.extName == ".eo" then
          Files[IO]
            .readAll(p)
            .through(utf8.decode)
            .compile
            .string
            .map(code => Some((p, code)))
        else None.pure[IO]
      )

  private val include: Opts[List[String]] =
    Opts.options[String](long = "include", help = "Rules to exclude.").orEmpty

  private val exclude: Opts[List[String]] =
    Opts.options[String](long = "exclude", help = "Rules to include.").orEmpty

  val inex: Opts[IncludeExclude] = (include, exclude).tupled.mapValidated {
    {
      case (Nil, Nil)     => Valid(IncludeExclude.Nothing)
      case (i :: is, Nil) => Valid(IncludeExclude.Include(NonEmptyList(i, is)))
      case (Nil, e :: es) => Valid(IncludeExclude.Exclude(NonEmptyList(e, es)))
      case (_ :: _, _ :: _) =>
        Invalid(
          NonEmptyList.one(
            """"--include" and "--exclude" options are mutually-exclusive. Please, specify just one."""
          )
        )
    }
  }

  val tmp: Opts[IO[Path]] = Opts
    .option[JPath](
      long = "out",
      help = "The directory where SARIF files should be stored.",
    )
    .map(p => Path.fromNioPath(p).pure[IO])
    .orElse(Files[IO].createTempDirectory.pure[Opts])

  val files: Opts[Stream[IO, (Path, String)]] = Opts
    .option[JPath](
      long = "files",
      help = "The directory with EO files.",
    )
    .map(p => readCodeFromFiles(Path.fromNioPath(p)))
    .orElse(readCodeFromStdin.map(s => (Path("."), s)).pure[Opts])
end PolystatOpts
