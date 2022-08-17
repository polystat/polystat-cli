package org.polystat.sarif

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import org.polystat.cli.BuildInfo
import org.latestbit.circe.adt.codec.JsonTaggedAdt
import JsonTaggedAdt.*

object Sarif:

  final val SARIF_VERSION = "2.1.0"
  final val SARIF_SCHEMA =
    "https://schemastore.azurewebsites.net/schemas/json/sarif-2.1.0-rtm.4.json"

  final case class SarifLog(
      runs: Seq[SarifRun],
      version: String = SARIF_VERSION,
      $schema: String = SARIF_SCHEMA,
  ) derives Codec.AsObject

  final case class SarifRun(
      tool: SarifTool,
      artifacts: Seq[SarifArtifact],
      results: Seq[SarifResult],
      invocations: Seq[SarifInvocation],
  ) derives Codec.AsObject

  final case class SarifArtifact(
      location: SarifArtifactLocation
  ) derives Codec.AsObject

  final case class SarifTool(driver: SarifDriver) derives Codec.AsObject

  final case class SarifDriver(
      name: String = "Polystat CLI",
      informationUri: String = BuildInfo.organizationHomepage.get.toString,
      semanticVersion: String = BuildInfo.version,
  ) derives Codec.AsObject

  final case class SarifResult(
      ruleId: String,
      level: SarifLevel,
      kind: SarifKind,
      message: SarifMessage,
      locations: Option[Seq[SarifLocation]],
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

  enum SarifLevel derives PureEncoderWithConfig, PureDecoderWithConfig:
    case ERROR, NONE

  given PureConfig[SarifLevel] =
    PureConfig.Values[SarifLevel](
      mappings = Map(
        "error" -> tagged[SarifLevel.ERROR.type],
        "none" -> tagged[SarifLevel.NONE.type],
      )
    )

  enum SarifKind derives PureEncoderWithConfig, PureDecoderWithConfig:
    case FAIL, PASS

  given PureConfig[SarifKind] =
    PureConfig.Values[SarifKind](
      mappings = Map(
        "fail" -> tagged[SarifKind.FAIL.type],
        "pass" -> tagged[SarifKind.PASS.type],
      )
    )

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
