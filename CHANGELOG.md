## Polystat v0.1.9

This release addressed the usability of the `--version` option, in particular:

* In addition to the version of the `polystat-cli` itself, `polystat --version` also prints the following:
  * `j2eo` version (the one when no `--j2eoVersion` is specified)
  * `py2eo` version
  * `odin` version
  * `far` version

* The names of downloaded `j2eo` jars now include the version downloaded, e.g. `j2eo-v0.5.3.jar`.

* The `--version` commandline option now has a shorter alternative `-v`, so that `polystat --version` and `polystat -v` are equivalent.

Besides, the names of analyzers where changed according to needs of @APotyomkin. You may want to update the config files. See the updated names by typing `polystat list`.
