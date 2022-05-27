package org.polystat.cli

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
import org.polystat.py2eo.parser.PythonLexer
import org.polystat.py2eo.transpiler.Transpile
import org.polystat.cli.BuildInfo

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
      ("mutualrec", EOOdinAnalyzer.advancedMutualRecursionAnalyzer),
      ("unjustified", EOOdinAnalyzer.unjustifiedAssumptionAnalyzer),
      ("liskov", EOOdinAnalyzer.liskovPrincipleViolationAnalyzer),
      ("directAccess", EOOdinAnalyzer.directStateAccessAnalyzer),
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

  def execute(usage: PolystatUsage): IO[Unit] =
    usage match
      case PolystatUsage.List(cfg) =>
        if cfg then IO.println(HoconConfig.keys.explanation)
        else
          analyzers.traverse_ { case (name, _) =>
            IO.println(name)
          }
      case PolystatUsage.Misc(version, config) =>
        if (version) then IO.println(BuildInfo.version)
        else
          readConfigFromFile(config.getOrElse(Path(".polystat.conf")))
            .flatMap(execute)
      case PolystatUsage.Analyze(
            lang,
            AnalyzerConfig(inex, input, tmp, fmts, out),
          ) =>
        val processedConfig = ProcessedConfig(
          filteredAnalyzers = filterAnalyzers(inex),
          tempDir = tmp match
            case Some(path) =>
              IO.println(s"Cleaning ${path.absolute}...") *> path.clean
            case None => Files[IO].createTempDirectory
          ,
          output = out,
          input = input,
          fmts = fmts,
        )
        val analysisResults: IO[Unit] =
          lang match
            case SupportedLanguage.EO => EO.analyze(processedConfig)
            case SupportedLanguage.Java(j2eo, j2eoVersion) =>
              Java.analyze(j2eoVersion, j2eo, processedConfig)
            case SupportedLanguage.Python => Python.analyze(processedConfig)
        analysisResults
  end execute

end Main