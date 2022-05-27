package org.polystat.sarif

import cats.syntax.foldable.*
import cats.syntax.functorFilter.*
import fs2.io.file.Path
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

import OdinAnalysisResult.*
import Sarif.*

final case class SarifOutput(filePath: Path, errors: List[OdinAnalysisResult]):
  private val sarifRun: SarifRun = SarifOutput.sarifRun(filePath, errors)

  val sarif: SarifLog = SarifLog(Seq(sarifRun))

  val json: Json = sarif.asJson.deepDropNullValues

end SarifOutput

object SarifOutput:

  def sarifRun(filePath: Path, errors: List[OdinAnalysisResult]): SarifRun =
    SarifRun(
      tool = SarifTool(SarifDriver()),
      results = errors.mapFilter(SarifOutput.sarifResult),
      invocations = errors.map(SarifOutput.sarifInvocation),
      artifacts = Seq(
        SarifArtifact(location =
          SarifArtifactLocation(uri = filePath.toNioPath.toUri.toString)
        )
      ),
    )
  def sarifInvocation(
      error: OdinAnalysisResult
  ): SarifInvocation =
    error match
      case AnalyzerFailure(ruleId, reason) =>
        SarifInvocation(
          toolExecutionNotifications = Seq(
            SarifNotification(
              level = Some(SarifLevel.ERROR),
              message = SarifMessage(reason.getMessage),
              exception = Some(
                SarifException(
                  kind = reason.getClass.getName,
                  message = reason.getMessage,
                )
              ),
              associatedRule = SarifReportingDescriptor(id = ruleId),
            )
          ),
          executionSuccessful = false,
        )
      case DefectsDetected(ruleId, _) =>
        SarifInvocation(
          toolExecutionNotifications = Seq(
            SarifNotification(
              level = None,
              message = SarifMessage(
                s"Analyzer \"${ruleId}\"completed successfully. Some errors were found"
              ),
              exception = None,
              associatedRule = SarifReportingDescriptor(id = ruleId),
            )
          ),
          executionSuccessful = true,
        )
      case Ok(ruleId) =>
        SarifInvocation(
          toolExecutionNotifications = Seq(
            SarifNotification(
              level = None,
              message = SarifMessage(
                s"Analyzer \"${ruleId}\"completed successfully. No errors were found"
              ),
              exception = None,
              associatedRule = SarifReportingDescriptor(id = ruleId),
            )
          ),
          executionSuccessful = true,
        )

  def sarifResult(error: OdinAnalysisResult): Option[SarifResult] =
    error match
      case AnalyzerFailure(_, _) => None
      case DefectsDetected(ruleId, message) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.ERROR,
            kind = SarifKind.FAIL,
            message = SarifMessage(message.mkString_("\n")),
            locations = None,
          )
        )
      case Ok(ruleId) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.NONE,
            kind = SarifKind.PASS,
            message = SarifMessage("No errors were found."),
            locations = None,
          )
        )
end SarifOutput
