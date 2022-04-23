package org.polystat

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.foldable._
import cats.syntax.functorFilter._
import cats.syntax.traverse._
import cats.syntax.monadError._
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
import PolystatOpts.IncludeExclude
import IncludeExclude._

object Main extends IOApp {

  val analyzers: List[(String, ASTAnalyzer[IO])] =
    // TODO: In Odin, change analyzer names to shorter ones.
    List(
      ("mutualrec", advancedMutualRecursionAnalyzer),
      ("unjustified", unjustifiedAssumptionAnalyzer),
    )

  def filterAnalyzers(
      inex: PolystatOpts.IncludeExclude
  ): List[ASTAnalyzer[IO]] = {
    inex match {
      case Exclude(exclude) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(!exclude.contains_(id))(a)
        }
      case Include(include) =>
        analyzers.mapFilter { case (id, a) =>
          Option.when(include.contains_(id))(a)
        }
      case Nothing => analyzers.map(_._2)
    }
  }

  def analyze(
      analyzers: List[ASTAnalyzer[IO]]
  )(code: String): IO[List[EOOdinAnalyzer.OdinAnalysisResult]] = {
    analyzers.traverse(a =>
      EOOdinAnalyzer
        .analyzeSourceCode(a)(code)(cats.Monad[IO], sourceCodeEoParser[IO]())
    )
  }

  def transformPath(other: Path): Path = {
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
  }

  def runPolystat(
      files: Stream[IO, (Path, String)],
      tmp: Path,
      filteredAnalyzers: List[ASTAnalyzer[IO]],
  ): IO[Unit] =
    for {
      _ <- IO.println(tmp)
      _ <- IO.println(filteredAnalyzers.map(_.name))
      _ <- files
        .evalMap { case (p, code) =>
          val tmpPath = tmp / transformPath(p)
          for {
            _ <- tmpPath.parent
              .map(Files[IO].createDirectories)
              .getOrElse(IO.unit)
            results <- analyze(filteredAnalyzers)(code).adaptError(e =>
              new Exception(p.toString + ": ", e)
            )
            sarifJson = SarifOutput(results).json.toString
            _ <- Stream
              .emits(sarifJson.getBytes)
              .through(Files[IO].writeAll(tmpPath))
              .compile
              .drain
          } yield ()
        }
        .compile
        .drain
    } yield ()

  val polystat = Command[PolystatConfig](
    name = "polystat",
    header = "Says hello!",
    helpFlag = true,
  )(PolystatConfig.opts).map { case PolystatConfig(tmp, files, inex, _) =>
    for {
      tmp <- tmp
      filtered = filterAnalyzers(inex)
      _ <- runPolystat(files, tmp, filtered)
    } yield ExitCode.Success
  }
  override def run(args: List[String]): IO[ExitCode] = {
    ConfigFile
      .getConfigOpts(args)
      .flatMap(confArgs =>
        IO.println(confArgs)
          .flatMap(_ => CommandIOApp.run[IO](polystat, confArgs ++ args))
          .map(code => code)
      )
  }
}

object ConfigFile {
  def optsFromConfig(path: Option[Path]): IO[List[String]] =
    path match {
      case None => IO.pure(List[String]())
      case Some(p) =>
        Files[IO]
          .readAll(p)
          .through { utf8.decode }
          .flatMap(s => Stream.emits(s.trim.split("\\s+")))
          .filter(_.nonEmpty)
          .compile
          .toList
    }

  def findConfigPath(args: List[String]) =
    (args.dropWhile(e => e != "--config") match {
      case _ :: y :: _ => Some(y)
      case _           => None
    }) match {
      case Some(p) => if (new java.io.File(p).exists) Some(Path(p)) else None
      case None    => None
    }

  def getConfigOpts(args: List[String]): IO[List[String]] =
    (findConfigPath _ andThen optsFromConfig)(args)
}
