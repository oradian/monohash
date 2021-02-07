#!/bin/bash

for BS in 8192 16384 32768 65536 131072 262144
do

MONOHASH_TARGET='/home/melezov/linux-5.11-rc6/'
ARGS="-DBUFFER_SIZE=$BS"

echo "baseline $BS"
java $ARGS -DhashMethod=0 -jar target/monohash-0.9.0.jar $MONOHASH_TARGET

echo "semaphores $BS"
java $ARGS -DhashMethod=1 -jar target/monohash-0.9.0.jar $MONOHASH_TARGET

echo "futures $BS"
java $ARGS -DhashMethod=2 -jar target/monohash-0.9.0.jar $MONOHASH_TARGET

done
