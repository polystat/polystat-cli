## Polystat v0.1.7

* Fixed a bug which caused the CLI to crash when analyzing an empty directory with J2EO.
* Fixed generation of the aggregated SARIF output. Now it generates a single `sarifLog` object with a `run` object for each of the files it was run on. The file location can be found at `sarifLog.runs[i].artifacts[0].location.uri`.
