package org.polystat.sarif

import fs2.io.file.Path
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

import Sarif.*

object AggregatedSarifOutput:
  def fromAnalyzed(
      analyzed: Map[Path, List[OdinAnalysisResult]]
  ): Seq[SarifLog] =
    analyzed.toSeq.map { case (path, results) =>
      SarifOutput(path, results).sarif
    }
end AggregatedSarifOutput
