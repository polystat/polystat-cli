package org.polystat.cli

import cats.effect.Sync
import cats.effect.IO
import fs2.io.file.Path
import higherkindness.droste.data.Fix
import org.polystat.far.FaR
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.core.ast.EOExpr
import org.polystat.odin.core.ast.EOProg
import org.eolang.parser.Syntax
import org.eolang.parser.Xsline
import org.eolang.parser.Spy
import org.cactoos.io.OutputTo
import org.cactoos.io.InputOf
import org.cactoos.Func
import com.jcabi.xml.XMLDocument
import com.jcabi.xml.XML
import org.polystat.cli.InputUtils.replaceExt
import java.nio.file.Path as JPath
import scala.jdk.CollectionConverters.*

object Far:

  private def program(xml: JPath): Func[String, XML] = new Func[String, XML]:
    def apply(locator: String): XML =
      val parts = locator.split("\\.")
      val name = parts(1)
      var obj: XML =
        new XMLDocument(xml).nodes("/program/objects").get(0)

      for idx <- 1 until parts.length do
        val objs = obj.nodes(s"o[@name='${parts(idx)}']")
        obj = objs.get(0);

      obj

  def runFar(pathToXml: JPath)(
      locator: String
  ): IO[java.util.Collection[String]] =
    IO.delay(new FaR().errors(program(pathToXml), locator))

  def analyze(ruleId: String)(pathToTmpDir: Path)(
      pathToCode: Path
  ): IO[OdinAnalysisResult] =
    val codeFileName: Path = pathToCode.fileName
    val codeFileNameNoExt: String =
      codeFileName.toString.splitAt(codeFileName.toString.indexOf("."))._1
    val pathToXml: JPath =
      (pathToTmpDir / codeFileName.replaceExt(".xml")).toNioPath
    println(pathToXml)

    for
      // parse EO to XMIR
      _ <- IO.delay(
        new Syntax(
          codeFileNameNoExt,
          new InputOf(pathToCode.toNioPath),
          new OutputTo(pathToXml),
        ).parse()
      )
      // run XSLT stuff
      _ <- IO.delay(
        new Xsline(
          new XMLDocument(pathToXml),
          new OutputTo(pathToXml),
          new Spy.None(),
        ).pass()
      )
      errors <- runFar(pathToXml)("Q." + codeFileNameNoExt)
        .handleErrorWith(_ =>
          runFar(pathToXml)("Q.class__" + codeFileNameNoExt)
        )
      result = OdinAnalysisResult.fromErrors(ruleId)(errors.asScala.toList)
    yield result
