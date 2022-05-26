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

case class HoconConfig(path: Path):

  import HoconConfig.{given, *}

  private object hocon:
    private val config =
      IO.blocking(ConfigFactory.parseFile(path.toNioPath.toFile))
    def apply(s: String): ConfigValue[IO, HoconConfigValue] =
      ConfigValue.eval(config.map(c => hoconAt(c)(keys.toplevel)(s)))
  end hocon

  private val lang = hocon(keys.inputLanguage).as[SupportedLanguage]
  private val j2eo = hocon(keys.j2eo).as[Path].option
  private val input = hocon(keys.input).as[Path].option.evalMap {
    case Some(path) => path.toInput
    case None       => IO.pure(Input.FromStdin)
  }
  private val tmp = hocon(keys.tempDir).as[Path].option
  private val outputsDirs =
    hocon(keys.outputsDirs).as[List[Path]].default(List())
  private val outputsFiles =
    hocon(keys.outputsFiles).as[List[Path]].default(List())
  private val outputsConsole =
    hocon(keys.outputsConsole).as[Boolean].default(false)

  private val outputs = (outputsDirs, outputsFiles, outputsConsole).parMapN {
    case (dirs, files, console) =>
      Output(dirs = dirs, files = files, console = console)
  }

  private val j2eoVersion = hocon(keys.j2eoVersion).as[String].option
  private val outputFormats =
    hocon(keys.outputFormats).as[List[OutputFormat]].default(List.empty)
  private val inex: ConfigValue[IO, Option[IncludeExclude]] =
    hocon(keys.includeRules)
      .as[Include]
      .widen[IncludeExclude]
      .or(hocon(keys.excludeRules).as[Exclude].widen[IncludeExclude])
      .option

  val config: ConfigValue[IO, PolystatUsage.Analyze] =
    (j2eo, j2eoVersion, inex, input, tmp, outputs, outputFormats, lang)
      .parMapN {
        case (
              j2eo,
              j2eoVersion,
              inex,
              input,
              tmp,
              outputs,
              outputFormats,
              lang,
            ) =>
          PolystatUsage.Analyze(
            language = lang match
              case Java(_, _) => Java(j2eo, j2eoVersion)
              case other      => other
            ,
            config = AnalyzerConfig(
              inex = inex,
              input = input,
              tmp = tmp,
              outputFormats = outputFormats,
              output = outputs,
            ),
          )
      }
end HoconConfig

object HoconConfig:

  object keys:
    val toplevel = "polystat"
    val inputLanguage = "lang"
    val input = "input"
    val tempDir = "tempDir"
    val outputTo = "outputTo"
    val outputFormats = "outputFormats"
    val includeRules = "includeRules"
    val excludeRules = "excludeRules"
    val j2eo = "j2eo"
    val outputs = "outputs"
    val outputsConsole = s"$outputs.console"
    val outputsDirs = s"$outputs.dirs"
    val outputsFiles = s"$outputs.files"
    val j2eoVersion = "j2eoVersion"
    val explanation = s"""
                       |$toplevel.$inputLanguage
                       |    The type of input files which will be analyzed. This key must be present.
                       |    Possible values:
                       |        "java" - only ".java" files will be analyzed.
                       |        "eo" - only ".eo" files will be analyzed.
                       |        "python" - only ".py" files will be analyzed.
                       |$toplevel.$j2eoVersion
                       |    Specifies the version of J2EO to download.
                       |$toplevel.$j2eo
                       |    Specifies the path to the J2EO executable.
                       |    If not specified, defaults to looking for j2eo.jar in the current working directory.
                       |    If it's not found, downloads it from maven central. The download only happens when this key is NOT provided. 
                       |$toplevel.$input
                       |    How the files are supplied to the analyzer. 
                       |    Can be either a path to a directory, path to a file, or absent. If absent, the code is read from standard input.
                       |$toplevel.$tempDir
                       |    The path to a directory where temporary analysis file will be stored. 
                       |    If not specified, defaults to an OS-generated temporary directory.
                       |$toplevel.$outputTo
                       |    The path to a directory where the results of the analysis are stored. 
                       |    If not specified, the results will be printed to console.
                       |$toplevel.$outputFormats
                       |    The formats for which output is generated. 
                       |    If it's an empty list or not specified, no output files are produced.
                       |$toplevel.$includeRules | $toplevel.$excludeRules 
                       |    Which rules should be included in / excluded from the analysis.
                       |    If both are specified, $toplevel.$includeRules takes precedence. 
                       |    The list of available rule specifiers can be found by running:
                       |        polystat.jar list
                       |$toplevel.$outputsConsole
                       |    Produce console output?
                       |$toplevel.$outputsDirs
                       |    A list of directories to write files to.
                       |$toplevel.$outputsFiles
                       |    A list of files to write aggregated output to. 
                       |
                       |""".stripMargin
  end keys
  extension (v: HoconConfigValue)
    def toNelString: Option[NonEmptyList[String]] =
      NonEmptyList.fromList(v.toListString)

    def toListString: List[String] =
      v.unwrapped.asInstanceOf[java.util.List[String]].asScala.toList
  end extension

  extension (s: String)
    def asSupportedLang: Option[SupportedLanguage] = s match
      case "eo"     => Some(EO)
      case "java"   => Some(Java(None, None))
      case "python" => Some(Python)
      case _        => None

    def asOutputFormat: Option[OutputFormat] = s match
      case "sarif" => Some(OutputFormat.Sarif)
      case _       => None
  end extension

  private given ConfigDecoder[String, SupportedLanguage] =
    ConfigDecoder[String].mapOption(keys.inputLanguage)(_.asSupportedLang)

  private given ConfigDecoder[String, Path] = ConfigDecoder[String].map(Path(_))
  private given ConfigDecoder[String, OutputFormat] =
    ConfigDecoder[String].mapOption("outputFormat")(_.asOutputFormat)

  private given Show[HoconConfigValue] = Show.fromToString

  private given ConfigDecoder[HoconConfigValue, Include] =
    ConfigDecoder[HoconConfigValue].mapOption(keys.includeRules)(
      _.toNelString.map(Include(_))
    )
  private given ConfigDecoder[HoconConfigValue, Exclude] =
    ConfigDecoder[HoconConfigValue].mapOption(keys.excludeRules)(
      _.toNelString.map(Exclude(_))
    )

  private given hocon2listFormat
      : ConfigDecoder[HoconConfigValue, List[OutputFormat]] =
    ConfigDecoder[HoconConfigValue].mapOption(keys.outputFormats) {
      _.toListString.traverse(_.asOutputFormat)
    }

  private given hocon2listPath: ConfigDecoder[HoconConfigValue, List[Path]] =
    ConfigDecoder[HoconConfigValue].mapOption("paths") {
      _.toListString.traverse(path =>
        ConfigDecoder[String, Path].decode(key = None, path).toOption
      )
    }
end HoconConfig
