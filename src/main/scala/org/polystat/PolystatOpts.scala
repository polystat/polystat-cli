package org.polystat
import cats.data.NonEmptyList
import cats.data.Validated
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Files
import fs2.io.file.Path
import org.polystat.PolystatConfig.*

import java.io.FileNotFoundException
import java.nio.file.Path as JPath

import IncludeExclude.*
import Validated.*
import InputUtils.toInput

object PolystatOpts extends IOApp.Simple:

  override def run: IO[Unit] =
    val opts = Seq(
      "--config",
      "aboba",
      "--version",

      // "c++",
      // "--in",
      // "tmp",
      // "--include",
      // "1",
      // "--include",
      // "2",
      // "--files",
      // "sandbox"
    )

    polystat
      .parse(opts)
      .map(a => a.flatMap(IO.println))
      .leftMap(IO.println)
      .merge
  end run

  def polystat: Command[IO[PolystatUsage]] = Command(
    name = "polystat",
    header = "Analyze code",
  ) {
    analyzeEO
      .orElse(analyzeJava)
      .orElse(analyzePython)
      .orElse(list)
      .orElse(misc)
  }

  def analyzeEO: Opts[IO[PolystatUsage.Analyze]] = Opts.subcommand(
    name = "eo",
    help = "Analyze EO intermediate files",
  ) {
    analyzerConfig.map(
      _.map(conf => PolystatUsage.Analyze(SupportedLanguage.EO, conf))
    )
  }

  def analyzeJava: Opts[IO[PolystatUsage.Analyze]] = Opts.subcommand(
    name = "java",
    help = "Analyze Java files",
  ) {
    analyzerConfig.map(
      _.map(conf => PolystatUsage.Analyze(SupportedLanguage.Java, conf))
    )
  }

  def analyzePython: Opts[IO[PolystatUsage.Analyze]] = Opts.subcommand(
    name = "py",
    help = "Analyze Python files",
  ) {
    analyzerConfig.map(
      _.map(conf => PolystatUsage.Analyze(SupportedLanguage.Python, conf))
    )
  }

  def list: Opts[IO[PolystatUsage]] = Opts.subcommand(
    name = "list",
    help = "List available analyzers.",
  ) {
    listConfig.map(IO.pure)
  }

  def listConfig: Opts[PolystatUsage] = Opts
    .flag(
      long = "config",
      help =
        """Display configuration keys for Polystat config file (.polystat.conf).""",
      short = "c",
    )
    .orFalse
    .map(config => PolystatUsage.List(config = config))

  def version = Opts
    .flag(
      long = "version",
      help = "Display the version of Polystat CLI.",
    )
    .orFalse

  def configPath = Opts
    .option[JPath](
      long = "config",
      help = "Read configuration from the given file.",
    )
    .map(Path.fromNioPath)
    .orNone

  def misc: Opts[IO[PolystatUsage.Misc]] =
    (version, configPath).mapN { case (version, config) =>
      PolystatUsage
        .Misc(version, config)
        .pure[IO]
    }

  def tmp: Opts[Option[Path]] = Opts
    .option[JPath](
      long = "tmp",
      help = "Where the temporary files will be stored.",
      metavar = "path",
    )
    .map(jp => Path.fromNioPath(jp))
    .orNone

  def in: Opts[IO[Input]] = Opts
    .option[JPath](
      long = "in",
      help = "Where input files are. If absent, read code from stdin.",
      metavar = "path",
    )
    .map(p => Path.fromNioPath(p).toInput)
    .orElse(Input.FromStdin.pure[IO].pure[Opts])

  private def include: Opts[Include] =
    Opts
      .options[String](long = "include", help = "Rules to exclude.")
      .map(Include(_))

  private def exclude: Opts[Exclude] =
    Opts
      .options[String](long = "exclude", help = "Rules to include.")
      .map(Exclude(_))

  def inex: Opts[Option[IncludeExclude]] = include.orElse(exclude).orNone

  private def sarif: Opts[Option[OutputFormat]] = Opts
    .flag(long = "sarif", help = "Produce SARIF JSON output.")
    .as(OutputFormat.Sarif)
    .orNone

  def outputFormats: Opts[List[OutputFormat]] =
    List(
      sarif
    ).sequence.map(_.flattenOption)

  // TODO: this doesn't really conform to spec,
  //       because decline doesn't support optional arguments to options
  //       https://github.com/bkirwi/decline/issues/350
  def files: Opts[Output] = Opts
    .option[JPath](
      long = "files",
      help = "Create output files in the specified path",
    )
    .map(p => Output.ToDirectory(Path.fromNioPath(p)))
    .orElse(Output.ToConsole.pure[Opts])

  def analyzerConfig: Opts[IO[AnalyzerConfig]] =
    (inex, in, tmp, outputFormats, files).mapN {
      case (inex, in, tmp, outputFormats, out) =>
        in.map(in => AnalyzerConfig(inex, in, tmp, outputFormats, out))
    }

end PolystatOpts
