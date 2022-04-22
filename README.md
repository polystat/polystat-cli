# Polystat CLI
This repository provides an alternative implementation to [Polystat CLI](https://github.com/polystat/polystat).

# Development
## Setup
This is a Scala sbt project. In order to build the project you need the following:
  * JDK 8+
  * sbt 1.6.2

Both can be easily fetched via [coursier](https://get-coursier.io/docs/overview). 

## Running the CLI
> `sbt run`

If you want to change the command-line arguments, edit the `.polystat` in the repository root.

## Generating the fat JAR
> `sbt assembly`

The jar can be then found at `target/scala-2.13/polyscat.jar`.

## Running the tests
> `sbt test`


