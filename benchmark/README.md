# MonoHash Benchmark

An example project which demonstrates using MonoHash in a bulid environment.

To try it out, run `sbt` in the benchmark folder and alternate between
fiddling with the root project files and executing `loadLib` in the sbt shell.  
The benchmark project will detect changes to the root project across SBT
sessions by persisting the export file used for diffing.

You can even try to fiddle with the files while the compilation is underway,
and the build will detect that and warn of changes.

Once a change is detected, it will output the diff, run the compilation
and store the new version of the lib into the `lib/monohash-<hash>.jar`
for consumption by the JHM benchmark.

Run `sbt bench` to run the actual benchmark.  
It is an alias for `;loadLib; jmh:run -wi 5 -w 5s -i 3 -r 5s -f 1 -t 1`
