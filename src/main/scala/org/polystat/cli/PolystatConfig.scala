package org.polystat.cli

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.apply.*
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Path
import org.polystat.cli.EOAnalyzer
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.cli.util.FileTypes.*

object PolystatConfig:

  final case class AnalyzerConfig(
      inex: Option[IncludeExclude],
      input: Input,
      tmp: Option[Directory],
      outputFormats: List[OutputFormat],
      output: Output,
  )

  final case class ProcessedConfig(
      filteredAnalyzers: List[EOAnalyzer],
      tempDir: Directory,
      input: Input,
      fmts: List[OutputFormat],
      output: Output,
  )

  enum SupportedLanguage:
    case EO, Python
    case Java(j2eo: Option[File], j2eoVersion: Option[String])
  end SupportedLanguage

  enum PolystatUsage:
    case Analyze(language: SupportedLanguage, config: AnalyzerConfig)
    case List(config: Boolean)
    case Misc(version: Boolean, configPath: Option[File])
  end PolystatUsage

  enum IncludeExclude:
    case Include(rules: NonEmptyList[String])
    case Exclude(rules: NonEmptyList[String])
  end IncludeExclude

  enum Input:
    case FromDirectory(path: Directory)
    case FromFile(path: File)
    case FromStdin
  end Input

  enum OutputFormat:
    case Sarif

  final case class Output(
      dirs: List[Directory],
      files: List[File],
      console: Boolean,
  )

end PolystatConfig
