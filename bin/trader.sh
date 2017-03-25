#!/usr/bin/env bash

cmd="java -cp target/trader-0.1.0-SNAPSHOT-all.jar \
          -Dlogback.configurationFile=conf/logback-default.xml \
          io.zjshen.trader.cli.traderCLI $@"
echo $cmd
exec $cmd