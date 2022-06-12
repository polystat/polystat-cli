package org.polystat.cli

import cats.effect.IO
import cats.effect.Sync
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.analysis.liskov.Analyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser

trait EOAnalyzer:
  def ruleId: String
  def analyze(
      tmpDir: Path,
      pathToSrcRoot: Path,
      pathToCode: Path,
      code: String,
  ): IO[OdinAnalysisResult]

object EOAnalyzer:

  val analyzers: List[EOAnalyzer] =
    // TODO: remove ruleIds from Odin analyzers
    List(
      fromOdinAstAnalyzer("mutual-recursion")(
        EOOdinAnalyzer.advancedMutualRecursionAnalyzer
      ),
      fromOdinAstAnalyzer("unjustified-assumption")(
        EOOdinAnalyzer.unjustifiedAssumptionAnalyzer
      ),
      fromOdinAstAnalyzer("liskov-principle")(
        EOOdinAnalyzer.liskovPrincipleViolationAnalyzer
      ),
      fromOdinAstAnalyzer("direct-state-access")(
        EOOdinAnalyzer.directStateAccessAnalyzer
      ),
      farEOAnalyzer("division-by-zero"),
    )

  def fromOdinAstAnalyzer(_ruleId: String)(a: ASTAnalyzer[IO]): EOAnalyzer =
    new EOAnalyzer:

      def ruleId: String = _ruleId
      def analyze(
          tmpDir: Path,
          pathToSrcRoot: Path,
          pathToCode: Path,
          code: String,
      ): IO[OdinAnalysisResult] =
        EOOdinAnalyzer
          .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO](2))
          .map {
            case f: OdinAnalysisResult.AnalyzerFailure =>
              f.copy(ruleId = ruleId)
            case d: OdinAnalysisResult.DefectsDetected =>
              d.copy(ruleId = ruleId)
            case ok: OdinAnalysisResult.Ok => ok.copy(ruleId = ruleId)
          }

  def farEOAnalyzer(_ruleId: String): EOAnalyzer = new EOAnalyzer:
    def ruleId: String = _ruleId
    def analyze(
        tmpDir: Path,
        pathToSrcRoot: Path,
        pathToCode: Path,
        code: String,
    ): IO[OdinAnalysisResult] =
      Far.analyze(
        ruleId = ruleId,
        pathToSrcRoot = pathToSrcRoot,
        pathToTmpDir = tmpDir,
        pathToCode = pathToCode,
      )
