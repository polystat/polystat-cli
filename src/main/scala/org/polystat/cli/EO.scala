package org.polystat.cli

import cats.data.NonEmptyList as Nel
import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Path
import io.circe.syntax.*
import org.polystat.cli.util.FileTypes.*
import org.polystat.cli.util.InputUtils.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import org.polystat.sarif.AggregatedSarifOutput
import org.polystat.sarif.SarifOutput

import PolystatConfig.*

object EO:

  private def displayResult(
      result: OdinAnalysisResult,
      indentDepth: Int,
  ): String =
    def indent(d: Int) = "\n" + "  " * d
    val indent1 = indent(indentDepth)
    val indent2 = indent(indentDepth + 1)
    val messages: Nel[String] = result match
      case OdinAnalysisResult.Ok(ruleId) =>
        Nel.one(s"""$indent1$ruleId: OK.""")
      case OdinAnalysisResult.DefectsDetected(ruleId, defects) =>
        defects.map(msg => s"""$indent2$msg""").prepend(s"$indent1$ruleId:")
      case OdinAnalysisResult.AnalyzerFailure(ruleId, reason) =>
        val reasonLines = reason.getMessage.trim
          .split(scala.util.Properties.lineSeparator)
          .map("  " * (indentDepth + 1) + "-- " + _)
          .mkString(scala.util.Properties.lineSeparator)
        Nel.one(
          s"""$indent1$ruleId: Analyzer failed with the following reason:\n${reasonLines}"""
        )

    messages.mkString_("")

  private def displayAnalyzed(
      analyzed: Vector[(File, List[OdinAnalysisResult])]
  ): IO[Unit] =
    analyzed.traverse_ { case (file, results) =>
      IO.println(
        s"""$file:${results.map(res => displayResult(res, 1)).mkString}
          """.stripMargin
      )
    }

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    def runAnalyzers(
        inputFiles: Vector[(File, String)]
    ): IO[Vector[(File, List[OdinAnalysisResult])]] =
      inputFiles
        .traverse { case (codePath, code) =>
          for
            _ <- IO.println(s"Analyzing $codePath...")
            analyzed <- cfg.filteredAnalyzers.traverse(a =>
              a.analyze(
                tmpDir = cfg.tempDir,
                pathToSrcRoot = cfg.input,
                pathToCode = codePath,
                code = code,
              ).handleError(e =>
                OdinAnalysisResult.AnalyzerFailure(a.ruleId, e)
              )
            )
          yield (codePath, analyzed.toList)
        }

    def writeToDirs(
        dirs: List[Directory],
        analyzed: Vector[(File, List[OdinAnalysisResult])],
    ): IO[Unit] =
      analyzed.traverse_ { case (codePath, results) =>
        for _ <- cfg.fmts.traverse_ { case OutputFormat.Sarif =>
            val sarifJson = SarifOutput(
              codePath,
              results,
            ).json.toString
            dirs.traverse_(out =>
              for
                sarifDir <- (out / "sarif").createDirIfDoesntExist
                outPath <-
                  codePath.toPath
                    .mount(
                      to = sarifDir,
                      relativelyTo = cfg.input,
                    )
                    .replaceExtThenCreateFile(newExt = ".sarif")
                _ <- IO.println(s"Writing results to $outPath...")
                _ <- writeOutputTo(outPath)(sarifJson)
              yield ()
            )
          }
        yield ()
      }

    def writeAggregate(
        files: List[File],
        analyzed: Vector[(File, List[OdinAnalysisResult])],
    ) =
      files.traverse_ { outputFile =>
        cfg.fmts.traverse_ { case OutputFormat.Sarif =>
          for
            _ <- IO.println(s"Writing aggregated output to $outputFile...")
            sariOutput = AggregatedSarifOutput(analyzed).json.toString
            _ <- writeOutputTo(outputFile)(sariOutput)
          yield ()
        }
      }

    for
      inputFiles <- readCodeFromDir(".eo", cfg.input).compile.toVector
      analyzed <- runAnalyzers(inputFiles)
      outputDirs <- cfg.output.dirs.traverse { outputPath =>
        for
          outputDir <- outputPath.createDirIfDoesntExist
          _ <- IO.println(s"Cleaning $outputDir before writing...")
          _ <- outputDir.clean
        yield outputDir
      }
      outputFiles <- cfg.output.files.traverse(_.createFileIfDoesntExist)
      _ <- writeToDirs(outputDirs, analyzed)
      _ <- writeAggregate(outputFiles, analyzed)
      _ <- if cfg.output.console then displayAnalyzed(analyzed) else IO.unit
    yield ()
    end for
  end analyze
end EO
