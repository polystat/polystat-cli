![logo](https://camo.githubusercontent.com/249d7357b20b54fb522bb75c82902fb6ae47d894c36015aba2c7b7f23a39b65d/68747470733a2f2f7777772e706f6c79737461742e6f72672f6c6f676f2e737667)

[![Continuous Integration](https://github.com/polystat/polystat-cli/actions/workflows/ci.yml/badge.svg)](https://github.com/polystat/polystat-cli/actions/workflows/ci.yml)

[![Maven Release](https://badgen.net/maven/v/metadata-url/https/repo1.maven.org/maven2/org/polystat/polystat-cli_3/maven-metadata.xml)](https://oss.sonatype.org/content/repositories/releases/org/polystat/polystat-cli_3/)
![GitHub](https://img.shields.io/github/license/polystat/polystat-cli)
![GitHub Release Date](https://img.shields.io/github/release-date/polystat/polystat-cli)
![GitHub all releases](https://img.shields.io/github/downloads/polystat/polystat-cli/total)
# Polystat CLI
This repository provides an alternative implementation to [Polystat](https://github.com/polystat/polystat). This tool's objective is to extend the functionality of the original implementation. These extensions include:
* A precise [specification](#full) for the command-line interface. 
* A configuration file that is not tied to the command-line interface.  
* A setup-free and customizable integration with the existing source-to-EO translators (specifically [j2eo](https://github.com/polystat/j2eo) and [py2eo](https://github.com/polystat/py2eo)). The following features are implemented for the `j2eo` translator:
    * Automatic downloading of the specified version from Maven Central
    * If you have `j2eo` installed locally, you can provide a path to it via a configuration option.
* The [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html) output of the analyzers can be produced in the following two forms:
    * A **directory** with the `.sarif.json` files, where each SARIF file corresponds to the file in the input directory. 
    * An **single file** where the outputs of the analyzers for all the analyzed files are aggregated in a single SARIF JSON object. 

...and many minor quality-of-life improvements. 

⚠ WARNING ⚠: The tool is still in the early stages of development, so feature suggestions and bug reports are more than welcome!

# Defects
This section describes the defects that the Polystat CLI can detect by analyzing the EO intermediate representation produced by the translators, such as `j2eo` and `py2eo`.

## Division by zero
The presence of this defect in the program means that some inputs may cause this program to fail with the ArithmeticException.

Comes from: https://github.com/polystat/far

Sample input (simplified EO translation):
```
+package org.polystat.far

[a b] > fartest
  add. > @
    a.div b
    div.
      b.div a
      a
```

Analyzer output:
```
\\perp at {a=\\any, b=0}\n\\perp at {a=0, b=\\any}\n\\perp at {a=0, b=0}
```

## Unanticipated mutual recursion
Comes from: https://github.com/polystat/odin

Unanticipated mutual recursion happens when a subclass redefines some of the methods of the superclass in such a way that one of the methods of the superclass becomes mutually-recursive with one of the redefined methods.

Sample input (simplified EO translation):

```
[] > a 
  b > @ 
  [self] > f 
    self > @ 
[] > b 
  [] > d 
    a > @
  [self] > g 
    self > @ 
[] > c 
  [] > e 
    a > @ 
    [self] > h 
      self.f self > @ 
[] > t 
  c.e > @ 
  [self] > f 
    self.h self > @ 
```

Analyzer output:

```
t: 
  t.h (was last redefined in "c.e") ->
  t.f ->
  t.h (was last redefined in "c.e")
```

## Unjustified assumptions about methods of superclasses
If the superclass contains this defect, this means that the inlining of one its
the methods is not safe, because doing so may lead to breaking changes in its subclasses. 

Comes from: https://github.com/polystat/odin

Sample input (simplified EO translation):

```
[] > base
  [self x] > f
    seq > @
      assert (x.less 9)
      x.add 1
  [self x] > g
    seq > @
      assert ((self.f self (x.add 1)).less 10)
      22
[] > derived
  base > @
  [self x] > f
    seq > @
      assert (5.less x)
      x.sub 1
```

Analyzer output:

```
Inlining calls in method g is not safe: doing so may break the behaviour of subclasses!
```

## Direct Access to the Base Class State
This defect means that the analyzed program contains the parts where the fields of the object are accessed directly. This probably means that the object with such fields breaks the incapsulation by exposing some of its private fields. 

Comes from: https://github.com/polystat/odin


Sample input (simplified EO translation):

```
[] > a
  memory > state
  [self new_state] > update_state
    self.state.write new_state > @
[] > b
  a > @
  [self new_state] > change_state_plus_two
    self.state.write (new_state.add 2) > @
```

Analyzer output:

```
Method 'change_state_plus_two' of object 'b' directly accesses state 'state' of base class 'a'
```

## Violation of the Liskov substitution principle
This defect means that some parts of the code violate the [Liskov substitution principle](https://en.wikipedia.org/wiki/Liskov_substitution_principle).

Comes from: https://github.com/polystat/odin

Sample input (simplified EO translation):

```
[] > base
  [self x] > f
    seq > @
      assert (x.less 9)
      x.add 1
[] > derived
  base > @
  [self x] > f
    seq > @
      assert (x.greater 9)
      x.sub 1
```

Analyzer output:

```
Method f of object derived violates the Liskov substitution principle as compared to version in parent object base
```


# Installation
## With coursier
If you have [coursier](https://get-coursier.io/docs/cli-installation) installed, then you can install the latest version of `polystat-cli` by running:
```
cs install --channel https://raw.githubusercontent.com/polystat/polystat-cli/master/coursier/polystat.json polystat
```
After that, you can simply run:
```
$ polystat --help
```

## Using a "fat" jar
The CLI is distributed as a "fat" jar (can be downloaded from [Github Releases](https://github.com/polystat/polystat-cli/releases)), so you can run without any prerequisites other than the [JRE](https://ru.wikipedia.org/wiki/Java_Runtime_Environment). If you have it installed, you can run `polystat-cli` by just executing:
```
$ java -jar polystat.jar <args>
```
It may be helpful to define an alias (the following works in most Linux and macos):
```
$ alias polystat="java -jar /path/to/polystat.jar"
```
And then simply run it like:
```
$ polystat <args>
```
More about the arguments you can pass can be found [here](#basic) and [here](#full).


# <a name="basic"></a> Basic usage

* If no arguments are provided to `polystat`, it will read the configuration from the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) config file in the current working directory. The default name for this file is `.polystat.conf` in the current working directory.

```
$ polystat
```

* If you want to read the configuration from the file located elsewhere, the following command can be used:

```
$ polystat --config path/to/hocon/config.conf
```


* Print all the available configuration keys that can be used in the config file.  

```
$ polystat list -c
``` 

* Print the rule IDs for all the available analyzers. 

```
$ polystat list
```

* Don't execute some rules during the analysis. This option is repeatable, so you can add any number of `--exclude rule` arguments to exclude all the specified rules. In the example below all the rules **but** `mutualrec` and `long` will be executed.
```
$ polystat eo --in tmp --exclude mutualrec --exclude long --sarif
```

* Execute _only_ the given rules during the analysis. This option is also repeatable. 
In the example below **only** `mutualrec` and `liskov` rules will be executed. 

```
$ polystat eo --in tmp --include mutualrec --include liskov --sarif
```

* Get the plain text console output from analyzing Java files located in the directory `src/main/java`. 

```
$ polystat java --in src/main/java --console
```

* Write the SARIF JSON files to `polystat_out/sarif` from analysing the `tmp` directory with `.eo` files.


```
$ polystat eo --in tmp --sarif --to dir=polystat_out
```


# <a name="full"></a> Full Usage Specification
This section covers all the options available in the CLI interface and their meanings. 

## Notation
The description follows [this guide](https://en.wikipedia.org/wiki/Command-line_interface#Command_description_syntax).
> Note: {a | b | c} means a set of _mutually-exclusive_ items.
```
polystat
    {eo | python}
    [--tmp <path>]
    [--in <path>]
    [{--include <rule...> | --exclude <rule...>}]
    [--sarif]
    [--to { console | dir=<path>| file=<path> }]...
polystat
    java
    [--j2eo-version <string>]
    [--j2eo <path>]
    [--tmp <path>]
    [--in <path>]
    [{--include <rule...> | --exclude <rule...>}]
    [--sarif]
    [--to { console | dir=<path>| file=<path> }]...
polystat [--version] [--help] [--config <path>]
polystat list [--config | -c]
```
## Input configuration
* The subcommand (`eo`, `java` or `python`) specifies which files should be analyzed (`.eo`, `.java` or `.py`). More languages can be added in the future. 
* `--in <file>` specifies the location of the source code to be analyzed. It can be either a directory with the files in the input language or a single file in the input language. If `--in` is not specified, defaults to reading the input language code from stdin.
* `--tmp <path>` specifies the path to the directory where the temporary files produced by analyzers are to be stored.  If `--tmp` is not specified, temporary files will be stored in the OS-created tempdir. It is assumed that the `path` supplied by `--tmp` points to an empty directory. If not, the contents of the `path` will be purged. If the `--tmp` option is specified but the directory it points to does not exist, it will be created. 
* The structure of the temporary directory is roughly as follows:
  * `<path>/eo` contains the generated `.eo` files.
  * `<path>/xmir` contains the generated `.xml` XMIR files (if any). 
  * `<path>/stdin` contains the files with the code read from stdin (if any).

## Configuration options
* <a name="inex"></a>`--include` and `--exclude` respectively define which rules should be included/excluded from the analysis run. These options are mutually exclusive, so specifying both should not be valid. If neither option is specified, all the available analyzers will be run. The list of available rule specifiers can be found via `polystat list` command.
* `--j2eo` (available only when running `polystat java`) option allows users to specify the path to the j2eo executable jar. If it's not specified, it looks for one in the current working diretory. 
If it's not present in the current working directory, download one from Maven Central (for now, the version is hardcoded to be 0.4.0).
* `--j2eo-version` (available only when running `polystat java`) option allows users to specify which version of `j2eo` should be downloaded.

## Output configuration
* `--sarif` option means that the command will produce the output in the [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) format in addition to output in other formats (if any). 
* `--to { console | dir=<path>| file=<path> }` is a repeatable option that specifies where the output should be written. If this option is not specified, no output is produced. 
* `--to dir=<path>` means that the files will be written to the given path. The path is assumed to be an empty directory. If it is not, its contents will be purged. If the `path` is specified but the directory it points to does not exist, it will be created. 
    * If an additional output format is specified (e.g. `--sarif`), then the files created by the analyzer will be written in the respective subdirectory. For example, in case of `--sarif`,  the SARIF files will be located in `path/sarif/`. The console output is not written anywhere. Therefore, if none of the output format options (e.g. `--sarif`) are specified, no files are produced. 
    * The output format options (e.g. `--sarif`) also determine the extension of the output files. In case of `--sarif` the extension would be `.sarif.json`.
    * If `--in` option specifies a directory, the structure of the output directory will be similar to the structure of the input directory. 
    * If `--in` specifies a single file, the file with the analysis output for this file will be written to the output directory. 
    * If `--in` is not specified, the generated file will be called `stdin` + the relevant extension. 

* `--to file=<path>` means that the results of analysis for all the files will be written to the file at the given path. For example, for `--sarif` output format this will a JSON array of `sarif-log` objects.

* `--to console` specifies whether the output should be written to console. The specification doesn't prevent the user from specifying multiple instances of this option. In this case, the output will be written to console as if just one instance of `--to console` was present. If it's not present the output is not written to console. 

## `polystat list`
* If no options are provided, prints the specifiers for all the available analyzer rules. 
* If `--config` or `-c` is specified, prints to console the descriptions of all the possible configuration keys for the HOCON config file.

## Other Options
* `--version` prints the version of `polystat-cli`, maybe with some additional information.
* `--help` displays some informative help messages for commands.
* `--config <path>` allows to configure Polystat from the specified HOCON config file. If not specified, reads configs from the file `.polystat.conf` in the current working directory.

# Configuration File
This section covers all the keys that can be used in the HOCON configuration files. The most relevant version of the information presented in this section can be printed to console by running:
```
$ polystat list --config
```
The example of the working config file can be found [here](.polystat.conf).

* `polystat.lang` - the type of input files which will be analyzed. This key must be present. Possible values:
    * "java" - only ".java" files will be analyzed.
    * "eo" - only ".eo" files will be analyzed.
    * "python" - only ".py" files will be analyzed.
* `polystat.j2eoVersion` - specifies the version of J2EO to download.
* `polystat.j2eo` - specifies the path to the J2EO executable. If not specified, defaults to looking for j2eo.jar in the current working directory. If it's not found, downloads it from maven central. The download only happens when this key is NOT provided. 
* `polystat.input` - specifies how the files are supplied to the analyzer. Can be either a path to a directory, path to a file, or absent. If absent, the code is read from standard input.
* `polystat.tempDir` - the path to a directory where temporary analysis file will be stored. If not specified, defaults to an OS-generated temporary directory.
* `polystat.outputTo` - the path to a directory where the results of the analysis are stored. If not specified, the results will be printed to console.
* `polystat.outputFormats` - the formats for which output is generated. If it's an empty list or not specified, no output files are produced.
* `polystat.includeRules` | `polystat.excludeRules` - specified which rules should be included in / excluded from the analysis. If both are specified, polystat.includeRules takes precedence. The list of available rule specifiers can be found by running:
    ```
    $ polystat.jar list
    ```
* `polystat.outputs.console` - specifies if the analysis results should be output to console. `false` by default.
* `polystat.outputs.dirs` - a list of directories to write files to.
* `polystat.outputs.files` - a list of files to write aggregated output to. 

# Development
Polystat CLI is an sbt Scala project. In order to build the project you need the following:
  * [JDK](https://ru.wikipedia.org/wiki/Java_Development_Kit) 8+
  * [sbt](https://www.scala-sbt.org/) 1.6.2

Both can be easily fetched via [coursier](https://get-coursier.io/docs/overview). 

Running the CLI:
```
$ sbt run
```

It's best to run this command in the interactive mode, because you can specify the cmdline args there.
However, for better turnaround time, it's better to tailor the `.polystat.conf` in the repository root for your needs and just run `run`.
If you want to change the command-line arguments, edit the `.polystat.conf` in the repository root.

The following command can be used to generate the "fat" JAR file. 
```
$ sbt assembly
```

The generated `.jar` file can be then found at `target/scala-3.1.2/polystat.jar`.

To run the tests use the relevant `sbt` task:
```
$ sbt test
```
