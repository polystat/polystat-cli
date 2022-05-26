## Polystat v0.1.6

In this release:

* `--files` option was renamed to `--to`. Its functionality was extended to account for more cases. See README.md for more information.

* a `--j2eo-version` was added, that specified which version of J2EO should be downloaded (if the download happens)

* Before writing to the output directory, its contents are cleaned up to avoid confusing the output from the analyzer with files from previous runs residual files. 
