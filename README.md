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

## Running on sandbox
```sh
sbt
run --config configs/sandbox
```
The output will appear in `../polystat-data/sandbox-polystat`

## Running on Hadoop

See [prerequisites](https://github.com/br4ch1st0chr0n3/j2eo#usage)

* First time
  ```sh
  chmod +x test-with-j2eo.sh
  ./test-with-j2eo.sh
  ```
* Next time
  * `sbt`
  * `run --config configs/hadoop`