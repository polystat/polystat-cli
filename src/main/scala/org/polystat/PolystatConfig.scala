package org.polystat

import cats.effect.IO
import cats.syntax.apply.*
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Path

import PolystatOpts.*

case class PolystatConfig(
    tmp: IO[Path],
    files: Stream[IO, (Path, String)],
    inex: IncludeExclude,
)

object PolystatConfig {
  def opts: Opts[PolystatConfig] =
    (tmp, files, inex).mapN(PolystatConfig.apply)
}
