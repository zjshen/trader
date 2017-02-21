#!/usr/bin/env bash

cmd="java -cp target/trader-0.1.0-SNAPSHOT-standalone.jar \
          -Dlogback.configurationFile=conf/logback-default.xml \
          io.zjshen.trader.cli.traderCli $@"
echo $cmd
exec $cmd