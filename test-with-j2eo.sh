#!/usr/bin/env bash

# Exit on error
set -e  

cd ..
git clone https://github.com/br4ch1st0chr0n3/j2eo || true
cd j2eo
./test-hadoop.sh
cd ../polyscat
sbt assembly
java -jar target/scala-2.13/polyscat.jar --files ../j2eo_hadoop/ --out ../polyscat_hadoop