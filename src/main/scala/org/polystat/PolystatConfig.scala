package org.polystat
import cats.effect.IO
import fs2.io.file.Path
import com.monovore.decline.Opts
import cats.syntax.apply.*
import fs2.Stream


case class PolystatConfig(
    tmp: IO[Path],
    files: Stream[IO, String],
    sarif: Boolean,
    includes: List[String],
    excludes: List[String]
)

object PolystatConfig {
  def opts: Opts[PolystatConfig] =
    (tmp, files, sarif, include, exclude).mapN(PolystatConfig.apply)
}
