package org.polystat.cli

import cats.effect.IO
import cats.effect.Sync
import com.jcabi.xml.XML
import com.jcabi.xml.XMLDocument
import fs2.io.file.Path
import higherkindness.droste.data.Fix
import org.cactoos.Func
import org.cactoos.io.InputOf
import org.cactoos.io.OutputTo
import org.eolang.parser.Spy
import org.eolang.parser.Syntax
import org.eolang.parser.Xsline
import org.polystat.cli.util.InputUtils.*
import org.polystat.far.FaR
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer.OdinAnalysisResult
import org.polystat.odin.core.ast.EOExpr
import org.polystat.odin.core.ast.EOProg

import java.nio.file.Path as JPath
import scala.jdk.CollectionConverters.*
import org.polystat.cli.util.FileTypes.*

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

  def analyze(
      ruleId: String,
      pathToSrcRoot: Directory,
      pathToTmpDir: Directory,
      pathToCode: File,
  ): IO[OdinAnalysisResult] =
    val codeFileNameNoExt: String = pathToCode.filenameNoExt
    val createPathToXml: IO[JPath] =
      (pathToTmpDir / "xmir").createDirIfDoesntExist.map(tmp =>
        pathToCode
          .mount(to = tmp, relativelyTo = pathToSrcRoot)
          .replaceExt(newExt = ".xml")
          .toPath
          .toNioPath
      )

    for
      pathToXml <- createPathToXml
      // parse EO to XMIR
      _ <- IO.delay(
        new Syntax(
          codeFileNameNoExt,
          new InputOf(pathToCode.toPath.toNioPath),
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
