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
  def json: Json = sarif.asJson.deepDropNullValues

  private val sarifRun: SarifRun = SarifRun(
    SarifTool(SarifDriver()),
    results = errors.mapFilter(sarifResult),
    invocations = errors.map(sarifInvocation),
  )

  val sarif: SarifLog = SarifLog(Seq(sarifRun))

  private def sarifInvocation(
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

  private def sarifResult(error: OdinAnalysisResult): Option[SarifResult] =
    error match
      case AnalyzerFailure(_, _) => None
      case DefectsDetected(ruleId, message) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.ERROR,
            kind = SarifKind.FAIL,
            message = SarifMessage(message.mkString_("\n")),
            locations = Seq(
              SarifLocation(physicalLocation =
                SarifPhysicalLocation(artifactLocation =
                  SarifArtifactLocation(uri = filePath.toNioPath.toUri.toString)
                )
              )
            ),
          )
        )
      case Ok(ruleId) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.NONE,
            kind = SarifKind.PASS,
            message = SarifMessage("No errors were found."),
            locations = Seq(
              SarifLocation(physicalLocation =
                SarifPhysicalLocation(artifactLocation =
                  SarifArtifactLocation(uri = filePath.toNioPath.toUri.toString)
                )
              )
            ),
          )
        )
end SarifOutput
