#!/usr/bin/env bash

# Exit on error
set -e  

cd ..
mkdir polystat-data
cd polystat-data
git clone https://github.com/br4ch1st0chr0n3/j2eo || true
cd j2eo
./test-hadoop.sh
cd ..
git clone https://github.com/br4ch1st0chr0n3/odin
cd odin
git checkout introducing_suuport_for_aliases
sbt publishLocal
cd ../../polystat-cli
sbt assembly
java -jar target/scala-2.13/polystat.jar --files ../polystat-data/j2eo-data/hadoop-eo --out ../polystat-data/hadoop-polystat