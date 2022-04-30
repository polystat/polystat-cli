package org.polystat

import io.circe.{Encoder, Decoder}
import io.circe.Json
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult

import OdinAnalysisResult.*
import Sarif.*

final case class SarifOutput(errors: List[OdinAnalysisResult]):
  def json: Json = sarif.asJson.deepDropNullValues

  private val sarifRun: SarifRun = SarifRun(
    SarifTool(SarifDriver()),
    results = errors.flatMap(sarifResult),
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

  private def sarifResult(error: OdinAnalysisResult): Option[SarifResult] =
    error match
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
end SarifOutput

object Sarif extends App:

  final val SARIF_VERSION = "2.1.0"
  final val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Documents/CommitteeSpecifications/2.1.0/sarif-schema-2.1.0.json"
  final val POLYSTAT_VERSION = "1.0-SNAPSHOT"

  given Encoder[SarifLog] = deriveEncoder[SarifLog]
  given Decoder[SarifLog] = deriveDecoder[SarifLog]
  final case class SarifLog(
      runs: Seq[SarifRun],
      version: String = SARIF_VERSION,
      $schema: String = SARIF_SCHEMA,
  )

  given Encoder[SarifRun] = deriveEncoder[SarifRun]
  given Decoder[SarifRun] = deriveDecoder[SarifRun]
  final case class SarifRun(
      tool: SarifTool,
      results: Seq[SarifResult],
      invocations: Seq[SarifInvocation],
  )

  given Encoder[SarifTool] = deriveEncoder[SarifTool]
  given Decoder[SarifTool] = deriveDecoder[SarifTool]
  final case class SarifTool(driver: SarifDriver)

  given Encoder[SarifDriver] = deriveEncoder[SarifDriver]
  given Decoder[SarifDriver] = deriveDecoder[SarifDriver]
  final case class SarifDriver(
      name: String = "Polystat",
      informationUri: String = "https://www.polystat.org/",
      semanticVersion: String = POLYSTAT_VERSION,
  )

  given Encoder[SarifResult] = deriveEncoder[SarifResult]
  given Decoder[SarifResult] = deriveDecoder[SarifResult]
  final case class SarifResult(
      ruleId: String,
      level: SarifLevel,
      kind: SarifKind,
      message: SarifMessage,
  )

  enum SarifLevel:
    case ERROR, NONE

  given Encoder[SarifLevel] with
    final def apply(a: SarifLevel): Json = a match
      case SarifLevel.ERROR => Json.fromString("error")
      case SarifLevel.NONE  => Json.fromString("none")
  end given

  given Decoder[SarifLevel] with
    def apply(c: io.circe.HCursor): Decoder.Result[SarifLevel] =
      c.get[String]("kind").map {
        case "error" => SarifLevel.ERROR
        case "none"  => SarifLevel.NONE
      }
  end given

  enum SarifKind:
    case FAIL, PASS

  given Encoder[SarifKind] with
    final def apply(a: SarifKind): Json = a match
      case SarifKind.PASS => Json.fromString("pass")
      case SarifKind.FAIL => Json.fromString("fail")
  end given

  given Decoder[SarifKind] with
    def apply(c: io.circe.HCursor): Decoder.Result[SarifKind] =
      c.get[String]("kind").map {
        case "pass" => SarifKind.PASS
        case "fail" => SarifKind.FAIL
      }
  end given
  given Encoder[SarifMessage] = deriveEncoder[SarifMessage]
  given Decoder[SarifMessage] = deriveDecoder[SarifMessage]
  final case class SarifMessage(text: String)

  given Encoder[SarifInvocation] = deriveEncoder[SarifInvocation]
  given Decoder[SarifInvocation] = deriveDecoder[SarifInvocation]
  final case class SarifInvocation(
      toolExecutionNotifications: Seq[SarifNotification],
      executionSuccessful: Boolean,
  )

  given Encoder[SarifNotification] = deriveEncoder[SarifNotification]
  given Decoder[SarifNotification] = deriveDecoder[SarifNotification]
  final case class SarifNotification(
      level: Option[SarifLevel],
      message: SarifMessage,
      exception: Option[SarifException],
      associatedRule: SarifReportingDescriptor,
  )

  given Encoder[SarifException] = deriveEncoder[SarifException]
  given Decoder[SarifException] = deriveDecoder[SarifException]
  final case class SarifException(kind: String, message: String)

  given Encoder[SarifReportingDescriptor] =
    deriveEncoder[SarifReportingDescriptor]
  given Decoder[SarifReportingDescriptor] =
    deriveDecoder[SarifReportingDescriptor]
  final case class SarifReportingDescriptor(id: String)

end Sarif
