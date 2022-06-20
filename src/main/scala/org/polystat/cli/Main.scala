package org.polystat.cli

import cats.data.NonEmptyList
import cats.data.ValidatedNel
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
import org.polystat.cli.util.FileTypes.*

import PolystatConfig.*
import IncludeExclude.*

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    for exitCode <- CommandIOApp.run(
        PolystatOpts.polystat.map(a => a.flatMap(execute).as(ExitCode.Success)),
        args,
      )
    yield exitCode

  def findMissingKeys(
      givenKeys: List[String],
      availableKeys: List[String],
  ): ValidatedNel[ValidationError, Unit] =
    val missingKeys = givenKeys.filterNot(rule => availableKeys.contains(rule))
    missingKeys.toNel match
      case Some(keys) => ValidationError.MissingAnalyzersKeys(keys).invalidNel
      case None       => ().validNel

  def validateInex(
      inex: Option[IncludeExclude]
  ): ValidatedNel[ValidationError, NonEmptyList[EOAnalyzer]] =
    val filtered = filterAnalyzers(inex).toNel
    val parsedKeys =
      inex
        .map {
          case Include(list) => list.toList
          case Exclude(list) => list.toList
        }
        .getOrElse(List())
    (
      findMissingKeys(parsedKeys, EOAnalyzer.analyzers.map(_.ruleId)),
      filtered match
        case Some(filtered) => filtered.valid
        case None           => ValidationError.NoAnalyzers(inex.get).invalidNel,
    ).mapN { case (_, filtered) =>
      filtered
    }

  def reportValidationErrors(
      errors: NonEmptyList[ValidationError]
  ): IO[Unit] =
    errors
      .traverse_ {
        case ValidationError.NoAnalyzers(inex) =>
          val message = inex match
            case Include(include) =>
              s"WARNING: The 'includeRules' key with values \"${include.mkString_(", ")}\" excludes all the analyzers, so none were run!"
            case Exclude(exclude) =>
              s"WARNING: The 'excludeRules' key with values \"${exclude.mkString_(", ")}\" excludes all the analyzers, so none were run!"
          IO.println(message)

        case ValidationError.MissingAnalyzersKeys(missing) =>
          missing.traverse_(rule =>
            IO.println(
              s"WARNING: The analyzer with the key '$rule' does not exist. " +
                s"Run 'polystat list' to get the list of the available keys."
            )
          )
      }

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
          for
            configFile <- config match
              case Some(file) => IO.pure(file)
              case None       => File.fromPathFailFast(Path(".polystat.conf"))
            _ <- IO.println(s"Reading configuration from $configFile...")
            config <- readConfigFromFile(configFile)
            result <- execute(config)
          yield result
      case PolystatUsage.Analyze(
            lang,
            AnalyzerConfig(inex, input, tmp, fmts, out),
          ) =>
        val ext = lang match
          case SupportedLanguage.Java(_, _) => ".java"
          case SupportedLanguage.Python     => ".py"
          case SupportedLanguage.EO         => ".eo"

        def analyze(filtered: NonEmptyList[EOAnalyzer]): IO[Unit] =
          for
            tempDir <- tmp match
              case Some(dir) =>
                IO.println(s"Cleaning ${dir.toPath.absolute}...") *>
                  dir.clean
              case None => Directory.createTempDirectory
            input <- input match
              case Input.FromDirectory(dir) => dir.pure[IO]
              case Input.FromFile(file) =>
                for
                  singleFileTmpDir <-
                    (tempDir / "singleFile").createDirIfDoesntExist
                  singleFileTmpPath =
                    singleFileTmpDir / (file.filenameNoExt + ext)
                  _ <- singleFileTmpPath.createFileIfDoesntExist
                  _ <- readCodeFromFile(ext, file)
                    .map(_._2)
                    .through(fs2.text.utf8.encode)
                    .through(Files[IO].writeAll(singleFileTmpPath))
                    .compile
                    .drain
                yield singleFileTmpDir
              case Input.FromStdin =>
                for
                  stdinTmpDir <-
                    (tempDir / "stdin").createDirIfDoesntExist
                  stdinTmpFilePath = stdinTmpDir / ("stdin" + ext)
                  _ <- stdinTmpFilePath.createFileIfDoesntExist
                  _ <-
                    readCodeFromStdin
                      .through(fs2.text.utf8.encode)
                      .through(Files[IO].writeAll(stdinTmpFilePath))
                      .compile
                      .drain
                yield stdinTmpDir
            processedConfig =
              ProcessedConfig(
                filteredAnalyzers = filtered,
                tempDir = tempDir,
                output = out,
                input = input,
                fmts = fmts,
              )
            _ <-
              lang match
                case SupportedLanguage.EO => EO.analyze(processedConfig)
                case SupportedLanguage.Java(j2eo, j2eoVersion) =>
                  Java.analyze(j2eoVersion, j2eo, processedConfig)
                case SupportedLanguage.Python => Python.analyze(processedConfig)
          yield ()

        validateInex(inex).fold(
          errors => reportValidationErrors(errors),
          filtered => analyze(filtered),
        )
  end execute

end Main
