package org.polystat

import cats.effect.IO
import cats.syntax.apply._
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Path

import PolystatOpts._

case class PolystatConfig(
    tmp: IO[Path],
    files: Stream[IO, (Path, String)],
    inex: IncludeExclude,
    config: Option[String]
)

object PolystatConfig {
  def opts: Opts[PolystatConfig] =
    (tmp, files, inex, argsFromConfig).mapN(PolystatConfig.apply)
}
