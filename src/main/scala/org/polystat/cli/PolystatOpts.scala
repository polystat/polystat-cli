package org.polystat.cli
import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import fs2.io.file.Files
import fs2.io.file.Path
import org.polystat.cli.PolystatConfig.*
import org.polystat.cli.util.InputUtils.toInput
import org.polystat.cli.util.FileTypes.*

import java.io.FileNotFoundException
import java.nio.file.Path as JPath

import IncludeExclude.*
import Validated.*

object PolystatOpts:

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
    (analyzerConfig, j2eo, j2eoVersion).mapN((conf, j2eo, j2eoVesion) =>
      conf.flatMap(conf =>
        for j2eo <- j2eo.traverse(File.fromPathFailFast)
        yield PolystatUsage.Analyze(
          SupportedLanguage.Java(j2eo, j2eoVesion),
          conf,
        )
      )
    )
  }

  def j2eoVersion: Opts[Option[String]] = Opts
    .option[String](
      long = "j2eo-version",
      help = "Version of j2eo to download.",
    )
    .orNone

  def j2eo: Opts[Option[Path]] = Opts
    .option[JPath](
      long = "j2eo",
      help = "Path to a j2eo executable.",
    )
    .map(Path.fromNioPath)
    .orNone

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
      short = "v",
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
      for config <- config.traverse(File.fromPathFailFast)
      yield PolystatUsage
        .Misc(version, config)
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

  private enum OutputArg:
    case File(path: Path)
    case Directory(path: Path)
    case Console
  end OutputArg

  private given Argument[OutputArg] with
    def read(string: String): ValidatedNel[String, OutputArg] =
      val KVArg = "(.*)=(.*)".r
      string match
        case KVArg(key, value) =>
          key match
            case "dir" =>
              Argument[JPath]
                .read(value)
                .map(path => OutputArg.Directory(Path.fromNioPath(path)))
            case "file" =>
              Argument[JPath]
                .read(value)
                .map(path => OutputArg.File(Path.fromNioPath(path)))
            case other =>
              Validated.invalidNel(s"Unknown key in `--to` option: $other")
        case "console" => Validated.valid(OutputArg.Console)
        case other     => Validated.invalidNel(s"Unknown argument: $string")
      end match
    end read
    def defaultMetavar: String = "console | dir=<path> | file=<path>"
  end given

  def files: Opts[Output] = Opts
    .options[OutputArg](
      long = "to",
      help = "Create output files in the specified path",
    )
    .orEmpty
    .map(args =>
      val initialState =
        Output(dirs = List(), files = List(), console = false)
      args.foldLeft(initialState) { case (acc, arg) =>
        arg match
          case OutputArg.File(path) =>
            acc.copy(files = acc.files.prepended(path))
          case OutputArg.Directory(path) =>
            acc.copy(dirs = acc.dirs.prepended(path))
          case OutputArg.Console =>
            if acc.console then acc else acc.copy(console = true)
      }
    )

  def analyzerConfig: Opts[IO[AnalyzerConfig]] =
    (inex, in, tmp, outputFormats, files).mapN {
      case (inex, in, tmp, outputFormats, out) =>
        for
          in <- in
          tmp <- tmp.traverse(Directory.fromPathFailFast)
        yield AnalyzerConfig(inex, in, tmp, outputFormats, out)
    }

end PolystatOpts
