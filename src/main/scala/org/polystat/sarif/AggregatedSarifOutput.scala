package org.polystat.sarif

import fs2.io.file.Path
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

import Sarif.*

case class AggregatedSarifOutput(
    analyzed: Seq[(Path, List[OdinAnalysisResult])]
)

object AggregatedSarifOutput:
  def fromAnalyzed(
      analyzed: Seq[(Path, List[OdinAnalysisResult])]
  ): Seq[SarifLog] =
    analyzed.map { case (path, results) =>
      SarifOutput(path, results).sarif
    }
end AggregatedSarifOutput
