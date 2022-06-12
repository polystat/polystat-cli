package org.polystat.cli

import cats.data.NonEmptyList
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
import org.polystat.cli.BuildInfo
import org.polystat.cli.EOAnalyzer.analyzers
import org.polystat.cli.util.InputUtils.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import org.polystat.py2eo.parser.PythonLexer
import org.polystat.py2eo.transpiler.Transpile

import PolystatConfig.*
import IncludeExclude.*
object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    for exitCode <- CommandIOApp.run(
        PolystatOpts.polystat.map(a => a.flatMap(execute).as(ExitCode.Success)),
        args,
      )
    yield exitCode

  def warnMissingKeys(
      givenKeys: List[String],
      availableKeys: List[String],
  ): IO[Unit] =
    givenKeys.traverse_(rule =>
      if availableKeys.contains(rule) then IO.unit
      else
        IO.println(
          s"WARNING: The analyzer with the key '$rule' does not exist. " +
            s"Run 'polystat list' to get the list of the available keys."
        )
    )

  def filterAnalyzers(
      inex: Option[IncludeExclude]
  ): List[EOAnalyzer] =
    inex match
      case Some(Exclude(exclude)) =>
        analyzers.mapFilter { case a =>
          Option.unless(exclude.contains_(a.ruleId))(a)
        }
      case Some(Include(include)) =>
        analyzers.mapFilter { case a =>
          Option.when(include.contains_(a.ruleId))(a)
        }
      case None => analyzers

  def execute(usage: PolystatUsage): IO[Unit] =
    usage match
      case PolystatUsage.List(cfg) =>
        if cfg then IO.println(HoconConfig.keys.explanation)
        else
          analyzers.traverse_ { case a =>
            IO.println(a.ruleId)
          }
      case PolystatUsage.Misc(version, config) =>
        if (version) then IO.println(BuildInfo.versionSummary)
        else
          readConfigFromFile(config.getOrElse(Path(".polystat.conf")))
            .flatMap(execute)
      case PolystatUsage.Analyze(
            lang,
            AnalyzerConfig(inex, input, tmp, fmts, out),
          ) =>
        for
          tempDir <- tmp match
            case Some(path) =>
              IO.println(s"Cleaning ${path.absolute}...") *>
                path.createDirIfDoesntExist.flatMap(_.clean)
            case None => Files[IO].createTempDirectory
          parsedKeys = inex
            .map {
              case Include(list) => list.toList
              case Exclude(list) => list.toList
            }
            .getOrElse(List())

          _ <- warnMissingKeys(parsedKeys, EOAnalyzer.analyzers.map(_.ruleId))
          processedConfig = ProcessedConfig(
            filteredAnalyzers = filterAnalyzers(inex),
            tempDir = tempDir,
            output = out,
            input = input,
            fmts = fmts,
          )
          analysisResults <-
            lang match
              case SupportedLanguage.EO => EO.analyze(processedConfig)
              case SupportedLanguage.Java(j2eo, j2eoVersion) =>
                Java.analyze(j2eoVersion, j2eo, processedConfig)
              case SupportedLanguage.Python => Python.analyze(processedConfig)
        yield ()
  end execute

end Main
