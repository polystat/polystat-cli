package org.polystat.cli

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Path
import io.circe.syntax.*
import org.polystat.cli.util.InputUtils.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import org.polystat.sarif.AggregatedSarifOutput
import org.polystat.sarif.SarifOutput
import org.polystat.cli.util.FileTypes.*

import PolystatConfig.*

object EO:

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
        for
          _ <- if cfg.output.console then IO.println(analyzed) else IO.unit
          _ <- cfg.fmts.traverse_ { case OutputFormat.Sarif =>
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
                    .replaceExtThenCreateFile(newExt = ".sarif.json")
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
    yield ()
    end for
  end analyze
end EO
