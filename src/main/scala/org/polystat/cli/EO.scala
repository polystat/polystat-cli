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

  def runAnalyzers(
      analyzers: List[ASTAnalyzer[IO]]
  )(code: String): IO[List[EOOdinAnalyzer.OdinAnalysisResult]] =
    analyzers.traverse(a =>
      EOOdinAnalyzer
        .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO]())
    )

  def analyze(cfg: ProcessedConfig): IO[Unit] =
    val inputFiles = readCodeFromInput(".eo", cfg.input)
    val analyzed = inputFiles
      .evalMap { case (codePath, code) =>
        for
          _ <- IO.println(s"Analyzing $codePath...")
          analyzed <- runAnalyzers(cfg.filteredAnalyzers)(code)
        yield (codePath, analyzed)
      }
      .compile
      .toVector

    def writeToDirs(analyzed: Vector[(Path, List[OdinAnalysisResult])]) =
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
            sariOutput = AggregatedSarifOutput
              .fromAnalyzed(analyzed)
              .asJson
              .deepDropNullValues
              .toString
            _ <- writeOutputTo(outputPath)(sariOutput)
          yield ()
        }
      }

    for
      analyzed <- analyzed
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
