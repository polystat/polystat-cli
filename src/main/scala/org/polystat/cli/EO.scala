package org.polystat.cli

import cats.effect.IO
import cats.syntax.foldable.*
import cats.syntax.traverse.*
import io.circe.syntax.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import org.polystat.sarif.AggregatedSarifOutput
import org.polystat.sarif.SarifOutput
import fs2.io.file.Path

import PolystatConfig.*
import InputUtils.*
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

object EO:

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    def runAnalyzers(inputFiles: Vector[(Path, String)]) = inputFiles
      .traverse { case (codePath, code) =>
        for
          _ <- IO.println(s"Analyzing $codePath...")
          analyzed <- cfg.filteredAnalyzers.traverse(a =>
            a.analyze(cfg.tempDir)(codePath)(code)
              .handleError(e => OdinAnalysisResult.AnalyzerFailure(a.ruleId, e))
          )
        yield (codePath, analyzed)
      }

    def writeToDirs(
        analyzed: Vector[(Path, List[OdinAnalysisResult])]
    ): IO[Unit] =
      analyzed.traverse_ { case (codePath, results) =>
        for
          _ <- if cfg.output.console then IO.println(analyzed) else IO.unit
          _ <- cfg.fmts.traverse_ { case OutputFormat.Sarif =>
            val sarifJson = SarifOutput(
              codePath,
              results,
            ).json.toString
            cfg.output.dirs.traverse_(out =>
              val outPath =
                out / "sarif" / codePath.replaceExt(".sarif.json")
              for
                _ <- IO.println(s"Writing results to $outPath...")
                _ <- writeOutputTo(outPath)(sarifJson)
              yield ()
            )
          }
        yield ()
      }

    def writeAggregate(analyzed: Vector[(Path, List[OdinAnalysisResult])]) =
      cfg.output.files.traverse_ { outputPath =>
        cfg.fmts.traverse_ { case OutputFormat.Sarif =>
          for
            _ <- IO.println(s"Writing aggregated output to $outputPath...")
            sariOutput = AggregatedSarifOutput(analyzed).json.toString
            _ <- writeOutputTo(outputPath)(sariOutput)
          yield ()
        }
      }

    def pathToDisplay(relPath: Path) = cfg.input match
      case Input.FromDirectory(dir) => dir / relPath
      // TODO: account for other cases
      // This should be the same pass that is created when running readCodeFromX
      case _ => relPath

    for
      inputFiles <- readCodeFromInput(".eo", cfg.input).compile.toVector
        .map(_.map { case (path, results) =>
          (pathToDisplay(path), results)
        })
      analyzed <- runAnalyzers(inputFiles)
      _ <- cfg.output.dirs.traverse_ { outDir =>
        for
          _ <- IO.println(s"Cleaning $outDir before writing...")
          _ <- outDir.clean
        yield ()
      }
      _ <- writeToDirs(analyzed)
      _ <- writeAggregate(analyzed)
    yield ()
    end for
  end analyze
end EO
