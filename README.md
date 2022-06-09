# Polystat CLI
This repository provides an alternative implementation to [Polystat](https://github.com/polystat/polystat).

# Basic usage

> `polystat`

If no arguments are provided to `polystat`, it will read the configuration from the [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) config file in the current working directory. The default name for this file is `.polystat.conf` in the current working directory. If you want to read the configuration from the file located elsewhere, the following command can be used:

```
polystat --config path/to/hocon/config.conf
```

> `polystat list -c` 

Prints all the available config keys.  
> `polystat list`

Prints the rule IDs for all the available analyzers. By default, all of them are enabled, however you can exclude some / include only the ones you want using the following commands:

> `polystat eo --in tmp --exclude mutualrec --sarif`

All the rules BUT the `mutualrec` will be executed.


> `polystat eo --in tmp --include mutualrec --include liskov --sarif`

Only `mutualrec` and `liskov` rules will be executed. 


> `polystat java --in src/main/java --console`

Get the plain text console output from analyzing Java files. The Java files are in the directory `src/main/java`. 

> `polystat eo --in tmp --sarif --to dir=polystat_out`

Write the [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) JSON files to `polystat_out/sarif` from analysing the `tmp` directory with `.eo` files.


# Full Usage Specification

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
* The subcommand specifies which files should be analyzed (`.eo`, `.java` or `.py`). More languages can be added in the future. 
* Analyzes the input files in the `--in` directory. If `--in` is not specified, defaults to reading the input language code from stdin. 
* `--in` can also accept a path which leads to a file. In this case, only the specified file will be analyzed. 
* The temporary files produced by analyzers are to be stored in `--tmp` directory.  If `--tmp` is not specified, temporary files will be stored in the OS-created tempdir. Each target language may have a different structure of the files in the temporary directory. It is assumed that the `path` supplied by `--tmp` points to an empty directory. If not, the contents of the `path` will be purged. If the `--tmp` option is specified but the directory it points to does not exist, it will be created. 

## Configuration options
* `--include` and `--exclude` respectively define which rules should be included/excluded from the analysis run. These options are mutually exclusive, so specifying both should not be valid. If neither option is specified, all the available analyzers will be run. The list of available rule specifiers can be found via `polystat list` command.
* `--j2eo` option allows users to specify the path to the j2eo executable jar. If it's not specified, it looks for one in the current working diretory. 
If it's not present in the current working directory, download one from Maven Central (for now, the version is hardcoded to be 0.4.0).
* `--j2eo-version` option allows users to specify which version of `j2eo` should be downloaded.

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
* If `--config` or `-c` is specified, prints to console the description of all the possible configuration keys for the HOCON config file. If not, prints the specifiers for all the available analyzer rules. 

## Other Options
* `--version` prints the version of the CLI tool, maybe with some additional information.
* `--help` displays some informative help message for commands.
* `--config <path>` allows to configure Polystat from the specified HOCON config file. If not specified, reads configs from the file `.polystat.conf` in the current working directory.

# Development
## Setup
This is an sbt Scala project. In order to build the project you need the following:
  * JDK 8+
  * sbt 1.6.2

Both can be easily fetched via [coursier](https://get-coursier.io/docs/overview). 

## Running the CLI
> `sbt run`

It's best to run this command in the interactive mode, because you can specify the cmdline args there.
However, for better turnaround time, it's better to tailor the `.polystat.conf` in the repository root for your needs and just run `run`.
If you want to change the command-line arguments, edit the `.polystat.conf` in the repository root.

## Generating the fat JAR
> `sbt assembly`

The jar can be then found at `target/scala-3.1.2/polystat.jar`.

## Running the tests
> `sbt test`


