package org.polystat.cli

import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser
import fs2.io.file.Files
import cats.effect.Sync
import cats.syntax.all.*
import fs2.io.file.Path
import cats.effect.IO

trait EOAnalyzer:
  def analyze(tmpDir: Path)(pathToCode: Path)(
      code: String
  ): IO[OdinAnalysisResult]

object EOAnalyzer:

  val analyzers: List[(String, EOAnalyzer)] =
    List(
      (
        "mutualrec",
        fromOdinAstAnalyzer(EOOdinAnalyzer.advancedMutualRecursionAnalyzer),
      ),
      (
        "unjustified",
        fromOdinAstAnalyzer(EOOdinAnalyzer.unjustifiedAssumptionAnalyzer),
      ),
      (
        "liskov",
        fromOdinAstAnalyzer(EOOdinAnalyzer.liskovPrincipleViolationAnalyzer),
      ),
      (
        "directAccess",
        fromOdinAstAnalyzer(EOOdinAnalyzer.directStateAccessAnalyzer),
      ),
      ("far", farEOAnalyzer),
    )

def fromOdinAstAnalyzer(a: ASTAnalyzer[IO]): EOAnalyzer =
  new EOAnalyzer:
    def analyze(
        tmpDir: Path
    )(pathToCode: Path)(code: String): IO[OdinAnalysisResult] =
      EOOdinAnalyzer
        .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO](2))

def farEOAnalyzer: EOAnalyzer = new EOAnalyzer:
  def analyze(tmpDir: Path)(pathToCode: Path)(
      code: String
  ): IO[OdinAnalysisResult] =
    Far.analyze(tmpDir)(pathToCode)
