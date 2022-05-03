package org.polystat

import cats.Show
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.functor.*
import cats.syntax.parallel.*
import cats.syntax.traverse.*
import ciris.ConfigDecoder
import ciris.ConfigValue
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue as HoconConfigValue
import fs2.io.file.Path
import lt.dvim.ciris.Hocon.*

import scala.jdk.CollectionConverters.*

import PolystatConfig.*
import SupportedLanguage.*
import IncludeExclude.*
import InputUtils.toInput

case class HoconConfig(path: Path) extends IOApp.Simple:

  override def run: IO[Unit] =
    for
      v <- config.load
      _ <- IO.println(v)
    yield ()

  extension (v: HoconConfigValue)
    def toNelString: Option[NonEmptyList[String]] =
      NonEmptyList.fromList(v.toListString)

    def toListString: List[String] =
      v.unwrapped.asInstanceOf[java.util.List[String]].asScala.toList
  end extension

  extension (s: String)
    def asSupportedLang: Option[SupportedLanguage] = s match
      case "eo"     => Some(EO)
      case "java"   => Some(Java)
      case "python" => Some(Python)
      case _        => None

    def asOutputFormat: Option[OutputFormat] = s match
      case "sarif" => Some(OutputFormat.Sarif)
      case _       => None
  end extension

  private given ConfigDecoder[String, SupportedLanguage] =
    ConfigDecoder[String].mapOption("lang")(_.asSupportedLang)

  private given ConfigDecoder[String, Path] = ConfigDecoder[String].map(Path(_))
  private given ConfigDecoder[String, OutputFormat] =
    ConfigDecoder[String].mapOption("outputFormat")(_.asOutputFormat)

  private given Show[HoconConfigValue] = Show.fromToString

  private given ConfigDecoder[HoconConfigValue, Include] =
    ConfigDecoder[HoconConfigValue].mapOption("includeRules")(
      _.toNelString.map(Include(_))
    )
  private given ConfigDecoder[HoconConfigValue, Exclude] =
    ConfigDecoder[HoconConfigValue].mapOption("excludeRules")(
      _.toNelString.map(Exclude(_))
    )

  private given ConfigDecoder[HoconConfigValue, List[OutputFormat]] =
    ConfigDecoder[HoconConfigValue].mapOption("outputFormats") {
      _.toListString.traverse(_.asOutputFormat)
    }

  private object hocon:
    private val config =
      IO.blocking(ConfigFactory.parseFile(path.toNioPath.toFile))
    def apply(s: String): ConfigValue[IO, HoconConfigValue] =
      ConfigValue.eval(config.map(c => hoconAt(c)("polystat")(s)))
  end hocon

  private val lang = hocon("lang").as[SupportedLanguage]
  private val input = hocon("input").as[Path].evalMap(_.toInput)
  private val tmp = hocon("tempDir").as[Path].option
  private val outputTo = hocon("outputTo").as[Path].option.map {
    case Some(path) => Output.ToDirectory(path)
    case None       => Output.ToConsole
  }
  private val outputFormats = hocon("outputFormats").as[List[OutputFormat]]
  private val inex: ConfigValue[IO, Option[IncludeExclude]] =
    hocon("includeRules")
      .as[Include]
      .widen[IncludeExclude]
      .or(hocon("excludeRules").as[Exclude].widen[IncludeExclude])
      .option

  val config: ConfigValue[IO, PolystatUsage.Analyze] =
    (inex, input, tmp, outputTo, outputFormats, lang).parMapN {
      case (inex, input, tmp, outputTo, outputFormats, lang) =>
        PolystatUsage.Analyze(
          language = lang,
          config = AnalyzerConfig(
            inex = inex,
            input = input,
            tmp = tmp,
            outputFormats = outputFormats,
            output = outputTo,
          ),
        )
    }
end HoconConfig
