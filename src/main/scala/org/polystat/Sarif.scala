package org.polystat

import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

import OdinAnalysisResult._
import Sarif._

case class SarifOutput(errors: List[OdinAnalysisResult]) {
  def json: Json = sarif.asJson.deepDropNullValues

  private val sarifRun: SarifRun = SarifRun(
    SarifTool(SarifDriver()),
    results = errors.flatMap(sarifResult),
    invocations = errors.map(sarifInvocation),
  )

  val sarif: SarifLog = SarifLog(Seq(sarifRun))

  private def sarifInvocation(
      error: OdinAnalysisResult
  ): SarifInvocation = {
    error match {
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
      case DefectDetected(ruleId, _) =>
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
    }

  }

  private def sarifResult(error: OdinAnalysisResult): Option[SarifResult] = {
    error match {
      case AnalyzerFailure(_, _) => None
      case DefectDetected(ruleId, message) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.ERROR,
            kind = SarifKind.FAIL,
            message = SarifMessage(message),
          )
        )
      case Ok(ruleId) =>
        Some(
          SarifResult(
            ruleId = ruleId,
            level = SarifLevel.NONE,
            kind = SarifKind.PASS,
            message = SarifMessage("No errors were found."),
          )
        )
    }
  }
}

object Sarif {

  final val SARIF_VERSION = "2.1.0"
  final val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Documents/CommitteeSpecifications/2.1.0/sarif-schema-2.1.0.json"
  final val POLYSTAT_VERSION = "1.0-SNAPSHOT"

  implicit val sarifLogEncoder: Encoder[SarifLog] = deriveEncoder[SarifLog]
  case class SarifLog(
      runs: Seq[SarifRun],
      version: String = SARIF_VERSION,
      $schema: String = SARIF_SCHEMA,
  )

  implicit val sarifRunEncoder: Encoder[SarifRun] = deriveEncoder[SarifRun]
  case class SarifRun(
      tool: SarifTool,
      results: Seq[SarifResult],
      invocations: Seq[SarifInvocation],
  )

  implicit val sarifToolEncoder: Encoder[SarifTool] = deriveEncoder[SarifTool]
  case class SarifTool(driver: SarifDriver)

  implicit val sarifDriverEncoder: Encoder[SarifDriver] =
    deriveEncoder[SarifDriver]
  case class SarifDriver(
      name: String = "Polystat",
      informationUri: String = "https://www.polystat.org/",
      semanticVersion: String = POLYSTAT_VERSION,
  )

  implicit val sarifResultEncoder: Encoder[SarifResult] =
    deriveEncoder[SarifResult]
  case class SarifResult(
      ruleId: String,
      level: SarifLevel,
      kind: SarifKind,
      message: SarifMessage,
  )

  sealed trait SarifLevel
  object SarifLevel {
    case object ERROR extends SarifLevel
    case object NONE extends SarifLevel
  }

  implicit val encodeSarifLevel: Encoder[SarifLevel] =
    new Encoder[SarifLevel] {
      final def apply(a: SarifLevel): Json = a match {
        case SarifLevel.ERROR => Json.fromString("error")
        case SarifLevel.NONE  => Json.fromString("none")
      }
    }

  sealed trait SarifKind
  object SarifKind {
    case object FAIL extends SarifKind
    case object PASS extends SarifKind
  }
  implicit val encodeSarifKind: Encoder[SarifKind] =
    new Encoder[SarifKind] {
      final def apply(a: SarifKind): Json = a match {
        case SarifKind.PASS => Json.fromString("pass")
        case SarifKind.FAIL => Json.fromString("fail")
      }
    }

  implicit val sarifMessageEncoder: Encoder[SarifMessage] =
    deriveEncoder[SarifMessage]
  case class SarifMessage(text: String)

  implicit val sarifInvocationEncoder: Encoder[SarifInvocation] =
    deriveEncoder[SarifInvocation]
  case class SarifInvocation(
      toolExecutionNotifications: Seq[SarifNotification],
      executionSuccessful: Boolean,
  )

  implicit val sarifNotificationEncoder: Encoder[SarifNotification] =
    deriveEncoder[SarifNotification]
  case class SarifNotification(
      level: Option[SarifLevel],
      message: SarifMessage,
      exception: Option[SarifException],
      associatedRule: SarifReportingDescriptor,
  )

  implicit val sarifExceptionEncoder: Encoder[SarifException] =
    deriveEncoder[SarifException]
  case class SarifException(kind: String, message: String)

  implicit
  val sarifReportingDescriptorEncoder: Encoder[SarifReportingDescriptor] =
    deriveEncoder[SarifReportingDescriptor]
  case class SarifReportingDescriptor(id: String)

}
