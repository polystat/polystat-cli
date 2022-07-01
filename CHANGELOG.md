## Polystat v0.1.12

This release introduced the following changes:

* `odin` was updated to v0.4.5. This change allows the detection of the Direct State Access defects. 
* `py2eo` was updated to v0.0.14.
* `polystat-cli`:
    * The generated SARIF output was changed slightly to be viewable with the [VSCode SARIF extension](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer). (thanks to @br4ch1st0chr0n3)
    * The output directories and temporary directories that were specified either on the command line or in the config file are now created automatically if they don't exist. 
