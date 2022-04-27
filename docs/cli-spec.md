## Usages

### Notation
The description follows [this guide](https://en.wikipedia.org/wiki/Command-line_interface#Command_description_syntax).
> Note: {a | b | c} means a set of _mutually-exclusive_ items.
```
polystat {eo | java | python} [--tmp <path>] [--in <path>] [{--include <rule...> | --exclude <rule...>}] [--sarif] [--files [path]]
polystat [--version] [--help] [--config <path>]
polystat list
polystat
```
## Input configuration
* The subcommand specifies which files should be analyzed (`.eo`, `.java` or `.py`). More languages can be added in the future. 
* Analyzes the input files in the `--in` directory. If `--in` is not specified, defaults to reading the input language code from stdin. 
* `--in` can also accept a path which leads to a file. In this case, only the specified file will be analyzed. 
* The temporary files produced by analyzers are to be stored in `--tmp` directory.  If `--tmp` is not specified, temporary files will be stored in the OS-created tempdir. Each target language may have a different structure of the files in the temporary directory. It is assumed that the `path` supplied by `--tmp` points to an empty directory. If not, the contents of the `path` will be purged.

## Configuration options
* `--include` and `--exclude` respectively define which rules should be included/excluded from the analysis run. These options are mutually exclusive, so specifying both should not be valid. If neither option is specified, all the available analyzers will be run. The list of available rule specifiers can be found via `polystat list` command.

## Output configuration
* `--sarif` option means that the output of the analysis will be in the [SARIF](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) format. If this option is not specified, only the plain text console output will be produced. By default, this output is printed to console. 
* `--files [path]` option specifies whether the output should be written to the output directory instead of writing it to the console.
If the path is specified, the command will write the files to the given path. If not, then the command will create a directory called `polystat_out` and write the files there. The path is assumed to be an empty directory. If it is not, its contents will be purged.
    * If an additional output format is specified (e.g. `--sarif`), then the files created by the analyzer will be written in the respective subdirectory. For example, in case of `--sarif`,  the SARIF files will be located in `path/sarif/`. The console output is not written anywhere. Therefore, if none of the output format options (e.g. `--sarif`) are specified, no files are produced. 
    * The output format options (e.g. `--sarif`) also determine the extension of the output files. In case of `--sarif` the extension would be `.sarif.json`.
    * If `--in` option specifies a directory, the structure of the output files will be similar to the structure of the input directory. 
    * If `--in` specifies a single file, the file with the analysis output for this file will be written to the output directory. 
    * If `--in` is not specified, the generated file will be called `stdin` + the relevant extension. 

## Other Options
* `--version` prints the version of the CLI tool, maybe with some additional information.
* `--help` displays some informative help message for commands.
* `--config <path>` allows to configure Polystat from the specified file. The format of the file is TBD.

## Calling Polystat without arguments
If no arguments are provided to `polystat`, it will read the configuration information from the config file in the current working directory. The format of the file is TBD.

## Use cases
1. Get the plain text console output from analyzing Java files. The Java files are in the directory `src/main/java`. 
```
polystat java --in src/main/java
```

2. Write the SARIF JSON files to `polystat_out/sarif` from analysing the `tmp` directory with `.eo` files.
```
polystat eo --in tmp --sarif --files
```
