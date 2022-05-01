package org.polystat

import cats.effect.{IOApp, IO, ExitCode}
import cats.syntax.all.*
import com.monovore.decline.Command
import com.monovore.decline.effect.CommandIOApp
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text.utf8
import org.polystat.odin.analysis.ASTAnalyzer
import org.polystat.odin.analysis.EOOdinAnalyzer
import org.polystat.odin.parser.EoParser.sourceCodeEoParser

import EOOdinAnalyzer.{
  advancedMutualRecursionAnalyzer,
  unjustifiedAssumptionAnalyzer,
}
import PolystatConfig.*
import IncludeExclude.*

object Main extends IOApp:

  // TODO: replace this with sbt-buildinfo
  val POLYSTAT_VERSION = "1.0-SNAPSHOT"

  val analyzers: List[(String, ASTAnalyzer[IO])] =
    // TODO: In Odin, change analyzer names to shorter ones.
    List(
      ("mutualrec", advancedMutualRecursionAnalyzer),
      ("unjustified", unjustifiedAssumptionAnalyzer),
    )

  def filterAnalyzers(
      inex: Option[IncludeExclude]
  ): List[ASTAnalyzer[IO]] =
    inex match
      case Some(Exclude(exclude)) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(!exclude.contains_(id))(a)
        }
      case Some(Include(include)) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(include.contains_(id))(a)
        }
      case None => analyzers.map(_._2)

  def analyze(
      analyzers: List[ASTAnalyzer[IO]]
  )(code: String): IO[List[EOOdinAnalyzer.OdinAnalysisResult]] =
    analyzers.traverse(a =>
      EOOdinAnalyzer
        .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO]())
    )

  def transformPath(other: Path): Path =
    val dotCount =
      other.names.map(_.toString).takeWhile(p => p == ".." || p == ".").length
    val newPath = other.toNioPath.subpath(
      Math.max(dotCount, 1),
      other.toNioPath.getNameCount,
    )
    val sarifJsonPath = Path(
      newPath.toString
        .splitAt(newPath.toString.lastIndexOf("."))
        ._1 + ".sarif.json"
    )

    sarifJsonPath
  end transformPath

  def listAnalyzers: IO[Unit] = analyzers.traverse_ { case (name, _) =>
    IO.println(name)
  }

  def readConfigFromFile(path: Path): IO[PolystatUsage.Analyze] = ???

  def execute(usage: PolystatUsage): IO[Unit] =
    usage match
      case PolystatUsage.List => listAnalyzers
      case PolystatUsage.Misc(version, config) =>
        if (version) then IO.println(POLYSTAT_VERSION)
        else
          readConfigFromFile(config.getOrElse(Path(".polystat.conf")))
            .flatMap(execute)
      case PolystatUsage.Analyze(lang, config) => IO.println("Not implemented!")

  // def runPolystat(
  //     files: Stream[IO, (Path, String)],
  //     tmp: Path,
  //     filteredAnalyzers: List[ASTAnalyzer[IO]],
  // ): IO[Unit] =
  //   for
  //     _ <- IO.println(tmp)
  //     _ <- IO.println(filteredAnalyzers.map(_.name))
  //     _ <- files
  //       .evalMap { case (p, code) =>
  //         val tmpPath = tmp / transformPath(p)
  //         for
  //           _ <- tmpPath.parent
  //             .map(Files[IO].createDirectories)
  //             .getOrElse(IO.unit)
  //           results <- analyze(filteredAnalyzers)(code).adaptError(e =>
  //             new Exception(p.toString + ": ", e)
  //           )
  //           sarifJson = SarifOutput(results).json.toString
  //           _ <- Stream
  //             .emits(sarifJson.getBytes)
  //             .through(Files[IO].writeAll(tmpPath))
  //             .compile
  //             .drain
  //         yield ()
  //         end for
  //       }
  //       .compile
  //       .drain
  //   yield ()

  override def run(args: List[String]): IO[ExitCode] =
    CommandIOApp.run(
      PolystatOpts.polystat.map(a => a.flatMap(execute).as(ExitCode.Success)),
      args,
    )
end Main
