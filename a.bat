@echo off
setlocal enabledelayedexpansion

for %%s in (8192, 16384, 32768, 65536, 131072, 262144) do (

set TARGET=s:\w2
set ARGS=-DBUFFER_SIZE=%%s
rem echo read
rem java -DhashMethod=read -jar target\monohash-0.9.0.jar !TARGET!

echo baseline %%s
java !ARGS! -DhashMethod=baseline -jar target\monohash-0.9.0.jar !TARGET!

echo parallelViaExecutor %%s
java !ARGS! -DhashMethod=parallelViaExecutor -jar target\monohash-0.9.0.jar !TARGET!

rem echo parallelViaAtomicSpinlock
rem java !ARGS! -DhashMethod=parallelViaAtomicSpinlock -jar target\monohash-0.9.0.jar !TARGET!

echo parallelViaSemaphores %%s
java !ARGS! -DhashMethod=parallelViaSemaphores -jar target\monohash-0.9.0.jar !TARGET!

)
