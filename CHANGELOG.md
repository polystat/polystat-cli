## Polystat v0.1.11

In this release `polystat-cli` is published to Maven Central! We have also added a coursier descriptor to the repository so that `polystat-cli` can be installed and managed by [coursier](https://get-coursier.io/)! To install `polystat-cli` with coursier, run the following command:
```
cs install --channel https://raw.githubusercontent.com/polystat/polystat-cli/master/coursier/polystat.json polystat
```

After that you should be able to just run `polystat-cli` as follows:
```
$ polystat -v
...
polystat-cli = 0.1.11
...
```

Besides, the analysis procedure is no longer run if the filtering options (i.e. `excludeRules` / `includeRules`) exclude all the analyzers. Instead, a warning message is printed:
```
WARNING: The 'includeRules' key with values "liskov" excludes all the analyzers, so none were run!
```
