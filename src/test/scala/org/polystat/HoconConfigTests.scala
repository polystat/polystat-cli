package org.polystat

import fs2.io.file.Path
import org.polystat.HoconConfig

import java.nio.file.Files
import java.nio.file.Paths

import PolystatConfig.*

class HoconConfigTests extends munit.FunSuite:

  import cats.effect.unsafe.implicits.global

  private case class ConfigTestCase(
      label: String,
      cfg: String,
      expected: Option[PolystatUsage.Analyze],
  )

  private val testcases = List(
    ConfigTestCase(
      label = "example config",
      cfg = """|polystat {
       |    lang = eo
       |    input = sandbox
       |    outputTo = tmp
       |    outputFormats = [sarif]
       |}
    """.stripMargin,
      expected = Some(
        PolystatUsage.Analyze(
          language = SupportedLanguage.EO,
          config = AnalyzerConfig(
            inex = None,
            input = Input.FromDirectory(Path("sandbox")),
            tmp = None,
            outputFormats = List(OutputFormat.Sarif),
            output = Output.ToDirectory(Path("tmp")),
          ),
        )
      ),
    ),
    ConfigTestCase(
      label = "empty",
      cfg = """""".stripMargin,
      expected = None,
    ),
  )

  private def runTestcases(tests: List[ConfigTestCase]) =
    tests.foreach { case ConfigTestCase(label, conf, expected) =>
      configFile(conf).test(label) { file =>
        val config = HoconConfig(file).config.load
        expected match
          case Some(expected) =>
            assertEquals(config.unsafeRunSync(), expected)
          case None =>
            intercept[ciris.ConfigException](config.unsafeRunSync())
        end match
      }
    }

  def configFile(config: String): FunFixture[Path] =
    FunFixture[Path](
      setup = test =>
        val path = Files.createTempFile("tmp", test.name)
        Path.fromNioPath(Files.write(path, config.getBytes))
      ,
      teardown = file => Files.deleteIfExists(file.toNioPath),
    )

  runTestcases(testcases)

end HoconConfigTests
