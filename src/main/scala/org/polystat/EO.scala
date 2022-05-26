package org.polystat

import cats.effect.IO
import PolystatConfig.*
import InputUtils.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import cats.syntax.traverse.*
import cats.syntax.foldable.*
import io.circe.syntax.*

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
        for analyzed <- runAnalyzers(cfg.filteredAnalyzers)(code)
        yield (codePath, analyzed)
      }
      .compile
      .toVector
      .map(_.toMap)
    val analyzeToDirs = inputFiles
      .evalMap { case (codePath, code) =>
        for
          _ <- IO.println(s"Analyzing $codePath...")
          analyzed <- runAnalyzers(cfg.filteredAnalyzers)(code)
          _ <- if cfg.output.console then IO.println(analyzed) else IO.unit
          _ <- cfg.fmts.traverse_ { case OutputFormat.Sarif =>
            val sarifJson = SarifOutput(
              codePath,
              analyzed,
            ).json.toString
            cfg.output.dirs.traverse_(out =>
              val outPath =
                out / "sarif" / codePath.replaceExt(".sarif.json")
              for
                _ <- IO.println(s"Writing results to $outPath")
                _ <- writeOutputTo(outPath)(sarifJson)
              yield ()
            )
          }
        yield ()
      }
      .compile
      .drain
    val analyzeAggregate = cfg.output.files.traverse_ { outputPath =>
      cfg.fmts.traverse_ { case OutputFormat.Sarif =>
        for
          _ <- IO.println(s"Writing aggregated output to $outputPath...")
          analyzed <- analyzed
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
      _ <- cfg.output.dirs.traverse_ { outDir =>
        for
          _ <- IO.println(s"Cleaning $outDir before writing...")
          _ <- outDir.clean
        yield ()
      }
      _ <- analyzeToDirs
      _ <- analyzeAggregate
    yield ()
    end for
  end analyze
end EO
