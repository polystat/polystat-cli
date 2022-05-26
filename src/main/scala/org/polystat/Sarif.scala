package org.polystat

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax.*
import fs2.io.file.Path
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import cats.syntax.foldable.*
import cats.syntax.functorFilter.*

import scala.CanEqual.derived

import OdinAnalysisResult.*
import Sarif.*

object AggregatedSarifOutput:
  def fromAnalyzed(
      analyzed: Map[Path, List[OdinAnalysisResult]]
  ): Seq[SarifLog] =
    analyzed.toSeq.map { case (path, results) =>
      SarifOutput(path, results).sarif
    }
end AggregatedSarifOutput

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

object Sarif:

  final val SARIF_VERSION = "2.1.0"
  final val SARIF_SCHEMA =
    "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Documents/CommitteeSpecifications/2.1.0/sarif-schema-2.1.0.json"

  final case class SarifLog(
      runs: Seq[SarifRun],
      version: String = SARIF_VERSION,
      $schema: String = SARIF_SCHEMA,
  ) derives Codec.AsObject

  final case class SarifRun(
      tool: SarifTool,
      results: Seq[SarifResult],
      invocations: Seq[SarifInvocation],
  ) derives Codec.AsObject

  final case class SarifTool(driver: SarifDriver) derives Codec.AsObject

  final case class SarifDriver(
      name: String = "Polystat",
      informationUri: String = "https://www.polystat.org/",
      semanticVersion: String = BuildInfo.version,
  ) derives Codec.AsObject

  final case class SarifResult(
      ruleId: String,
      level: SarifLevel,
      kind: SarifKind,
      message: SarifMessage,
      locations: Seq[SarifLocation],
  ) derives Codec.AsObject

  final case class SarifLocation(
      physicalLocation: SarifPhysicalLocation
  ) derives Codec.AsObject

  final case class SarifPhysicalLocation(
      artifactLocation: SarifArtifactLocation
  ) derives Codec.AsObject

  final case class SarifArtifactLocation(
      uri: String
  ) derives Codec.AsObject

  enum SarifLevel:
    case ERROR, NONE

  given Encoder[SarifLevel] with
    final def apply(a: SarifLevel): Json = a match
      case SarifLevel.ERROR => Json.fromString("error")
      case SarifLevel.NONE  => Json.fromString("none")
  end given

  given Decoder[SarifLevel] with
    def apply(c: io.circe.HCursor): Decoder.Result[SarifLevel] =
      c.get[String]("level").map {
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

  final case class SarifMessage(text: String) derives Codec.AsObject

  final case class SarifInvocation(
      toolExecutionNotifications: Seq[SarifNotification],
      executionSuccessful: Boolean,
  ) derives Codec.AsObject

  final case class SarifNotification(
      level: Option[SarifLevel],
      message: SarifMessage,
      exception: Option[SarifException],
      associatedRule: SarifReportingDescriptor,
  ) derives Codec.AsObject

  final case class SarifException(kind: String, message: String)
      derives Codec.AsObject

  final case class SarifReportingDescriptor(id: String) derives Codec.AsObject

end Sarif
