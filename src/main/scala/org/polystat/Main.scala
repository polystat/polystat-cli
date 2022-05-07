package org.polystat

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.effect.CommandIOApp
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text.utf8
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.parser.EoParser.sourceCodeEoParser

import EOOdinAnalyzer.{
  advancedMutualRecursionAnalyzer,
  unjustifiedAssumptionAnalyzer,
}
import PolystatConfig.*
import IncludeExclude.*
import InputUtils.*
object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    for exitCode <- CommandIOApp.run(
        PolystatOpts.polystat.map(a => a.flatMap(execute).as(ExitCode.Success)),
        args,
      )
    yield exitCode

  val analyzers: List[(String, ASTAnalyzer[IO])] =
    // TODO: In Odin, change analyzer names to shorter ones.
    List(
      ("mutualrec", advancedMutualRecursionAnalyzer),
      ("unjustified", unjustifiedAssumptionAnalyzer),
    )

  def filterAnalyzers(
      inex: Option[IncludeExclude]
  ): List[ASTAnalyzer[IO]] =
    inex match
      case Some(Exclude(exclude)) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(!exclude.contains_(id))(a)
        }
      case Some(Include(include)) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(include.contains_(id))(a)
        }
      case None => analyzers.map(_._2)

  def analyze(
      analyzers: List[ASTAnalyzer[IO]]
  )(code: String): IO[List[EOOdinAnalyzer.OdinAnalysisResult]] =
    analyzers.traverse(a =>
      EOOdinAnalyzer
        .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO]())
    )

  def listAnalyzers: IO[Unit] = analyzers.traverse_ { case (name, _) =>
    IO.println(name)
  }

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

  def analyzeEO(
      inputFiles: Stream[IO, (Path, String)],
      outputFormats: List[OutputFormat],
      out: Output,
      filteredAnalyzers: List[ASTAnalyzer[IO]],
  ): IO[Unit] =
    inputFiles
      .evalMap { case (codePath, code) =>
        for
          _ <- IO.println(s"Analyzing $codePath...")
          analyzed <- analyze(filteredAnalyzers)(code)
          _ <- out match
            case Output.ToConsole => IO.println(analyzed)
            case Output.ToDirectory(out) =>
              outputFormats.traverse_ { case OutputFormat.Sarif =>
                val outPath =
                  out / "sarif" / codePath.replaceExt(".sarif.json")
                val sarifJson = SarifOutput(analyzed).json.toString
                IO.println(s"Writing results to $outPath") *>
                  writeOutputTo(outPath)(sarifJson)
              }
        yield ()
      }
      .compile
      .drain

  def execute(usage: PolystatUsage): IO[Unit] =
    usage match
      case PolystatUsage.List => listAnalyzers
      case PolystatUsage.Misc(version, config) =>
        if (version) then IO.println(BuildInfo.version)
        else
          readConfigFromFile(config.getOrElse(Path(".polystat.conf")))
            .flatMap(execute)
      case PolystatUsage.Analyze(
            lang,
            AnalyzerConfig(inex, input, tmp, fmts, out),
          ) =>
        val filteredAnalyzers = filterAnalyzers(inex)
        val tempDir: IO[Path] = tmp match
          case Some(path) => IO.pure(path)
          case None       => Files[IO].createTempDirectory
        val inputExt: String = lang match
          case SupportedLanguage.EO     => ".eo"
          case SupportedLanguage.Java   => ".java"
          case SupportedLanguage.Python => ".py"

        val analysisResults: IO[Unit] =
          lang match
            case SupportedLanguage.EO =>
              val inputFiles = readCodeFromInput(ext = inputExt, input = input)
              analyzeEO(
                inputFiles = inputFiles,
                outputFormats = fmts,
                out = out,
                filteredAnalyzers = filteredAnalyzers,
              )
            case SupportedLanguage.Java =>
              for
                tmp <- tempDir
                _ <- input match
                  case Input.FromStdin      => ???
                  case Input.FromFile(path) => ???
                  case Input.FromDirectory(path) =>
                    J2EO.run(inputDir = path, outputDir = tmp).as(tempDir)
                inputFiles = readCodeFromInput(
                  ".eo",
                  Input.FromDirectory(tmp),
                )
                _ <- analyzeEO(
                  inputFiles = inputFiles,
                  outputFormats = fmts,
                  out = out,
                  filteredAnalyzers = filteredAnalyzers,
                )
              yield ()
            case SupportedLanguage.Python =>
              IO.println("Analyzing Python is not implemented yet!")
        analysisResults
  end execute

end Main
