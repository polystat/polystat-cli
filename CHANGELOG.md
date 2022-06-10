## Polystat v0.1.8

This release added a [FaR](https://github.com/polystat/far) analyzer to Polystat CLI. In addition, the following problems were addressed:

* Paths to files in `.sarif.json` files should be generated correctly now. 
* Output and temporary directories that are specified are created if they don't exist. 
* Analyzers names printed by `polystat list` and the ones used in `.sarif.json` files are now consistent (and shorter). They can also be changed easily if necessary. The analyzer names can be found by running:
```
java -jar polystat.jar list
```
* Using odin v0.4.3 (with better support for J2EO output). 
