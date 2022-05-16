package org.polystat

import cats.effect.IO
import PolystatConfig.*
import InputUtils.*
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import cats.syntax.traverse.*
import cats.syntax.foldable.*

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
    inputFiles
      .evalMap { case (codePath, code) =>
        for
          _ <- IO.println(s"Analyzing $codePath...")
          analyzed <- runAnalyzers(cfg.filteredAnalyzers)(code)
          _ <- cfg.output match
            case Output.ToConsole => IO.println(analyzed)
            case Output.ToDirectory(out) =>
              cfg.fmts.traverse_ { case OutputFormat.Sarif =>
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
  end analyze
end EO
