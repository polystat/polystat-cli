package org.polystat

import cats.effect.{IOApp, IO, ExitCode}
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.effect.CommandIOApp
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text.utf8
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser

import EOOdinAnalyzer.{
  advancedMutualRecursionAnalyzer,
  unjustifiedAssumptionAnalyzer,
}
import PolystatConfig.*
import IncludeExclude.*
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

object Main extends IOApp:

  val optsFromConfig: IO[List[String]] = Files[IO]
    .readAll(Path(".polystat"))
    .through(utf8.decode)
    .flatMap(s => Stream.emits(s.trim.split("\\s+")))
    .filter(_.nonEmpty)
    .compile
    .toList

  override def run(args: List[String]): IO[ExitCode] =
    optsFromConfig.flatMap(confargs =>
      CommandIOApp.run(
        PolystatOpts.polystat.map(a => a.flatMap(execute).as(ExitCode.Success)),
        args ++ confargs,
      )
    )

  // TODO: replace this with sbt-buildinfo
  val POLYSTAT_VERSION = "1.0-SNAPSHOT"

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

  def readConfigFromFile(path: Path): IO[PolystatUsage.Analyze] = IO
    .println("Cannot read config from file. Not implemented!")
    .asInstanceOf[IO[PolystatUsage.Analyze]]

  def execute(usage: PolystatUsage): IO[Unit] =
    usage match
      case PolystatUsage.List => listAnalyzers
      case PolystatUsage.Misc(version, config) =>
        if (version) then IO.println(POLYSTAT_VERSION)
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

        val inputFiles: Stream[IO, (Path, String)] =
          readCodeFromInput(ext = inputExt, input = input)

        val analysisResults: Stream[IO, Unit] =
          inputFiles.evalMap { case (codePath, code) =>
            val analyzed = analyze(filteredAnalyzers)(code)
            val output = out match
              case Output.ToConsole => analyzed.flatMap(IO.println)
              case Output.ToDirectory(out) =>
                Stream
                  .emits(fmts)
                  .evalMap { case OutputFormat.Sarif =>
                    val outPath =
                      out / "sarif" / codePath.replaceExt(".sarif.json")
                    for
                      _ <- IO.println(codePath)
                      _ <- IO.println(outPath)
                      _ <- outPath.parent
                        .map(Files[IO].createDirectories)
                        .getOrElse(IO.unit)
                      _ <- analyzed
                        .map(SarifOutput(_).json.toString)
                        .flatMap(sarifJson =>
                          Stream
                            .emits(sarifJson.getBytes)
                            .through(Files[IO].writeAll(outPath))
                            .compile
                            .drain
                        )
                    yield ()
                    end for
                  }
                  .compile
                  .drain
            output
          }

        analysisResults.compile.drain
    end match
  end execute

end Main
