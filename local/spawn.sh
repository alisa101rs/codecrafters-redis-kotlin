#!/bin/sh
set -e
exec java -jar ./target/java_redis.jar "$@"