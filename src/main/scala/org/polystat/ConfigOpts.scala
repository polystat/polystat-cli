package org.polystat

import cats.effect.IO
import com.monovore.decline.Opts
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text.utf8

import java.nio.file.{Path => JPath}

object ConfigOpts {
    
    // via Command
    // read file if such file exists
    // return list of parameters
    // append it in main

  def readArgsFromConfig(path: Path): IO[List[String]] =
    Files[IO]
    .readAll(path)
    .handleErrorWith(_=> Stream.empty)
    .through{utf8.decode}
    .flatMap(s => Stream.emits(s.trim.split("\\s+")))
    .filter(_.nonEmpty)
    .compile
    .toList
  
  val argsFromConfig: Opts[IO[List[String]]] = Opts
    .option[JPath]( 
      long = "config",
      help = "Read CLI arguments from given configuration file." +
        "Please, use either CL arguments or read them from config.",
    )
    .map(p => readArgsFromConfig(Path.fromNioPath(p)))
    // TODO
    // .orElse(s => List().pure[Opts])
    
}
