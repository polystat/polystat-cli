package org.polystat

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.apply.*
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Path

object PolystatConfig:

  final case class AnalyzerConfig(
      inex: Option[IncludeExclude],
      input: Input,
      tmp: Option[Path],
      outputFormats: List[OutputFormat],
      output: Output,
  )

  enum SupportedLanguage:
    case EO, Java, Python

  enum PolystatUsage:
    case Analyze(language: SupportedLanguage, config: AnalyzerConfig)
    case List(config: Boolean)
    case Misc(version: Boolean, configPath: Option[Path])
  end PolystatUsage

  enum IncludeExclude:
    case Include(rules: NonEmptyList[String])
    case Exclude(rules: NonEmptyList[String])
  end IncludeExclude

  enum Input:
    case FromDirectory(path: Path)
    case FromFile(path: Path)
    case FromStdin
  end Input

  enum OutputFormat:
    case Sarif

  enum Output:
    case ToDirectory(path: Path)
    case ToConsole
  end Output

end PolystatConfig
