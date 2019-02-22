# MonoHash
[![Build Status](https://travis-ci.org/oradian/monohash.svg?branch=master)](https://travis-ci.org/oradian/monohash)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.oradian.infra/monohash/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.oradian.infra/monohash)
[![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT)

**MonoHash** is a hashing library designed to work with monorepos containing multiple projects.  
It's primary purpose is to allow for lean CI/CD cache invalidation which will only build relevant changes while ignoring projects which are impervious to these particular changes.

## Rationale

Having a single monorepo-level hash, such as Git's SHA-1 is not sufficiently fine-grained for the purposes of caching, as any change to the documentation files, .gitignores, or files that are not relevant to the project we intend to cache will invalidate that single hash - which means all projects will need to be rebuilt.

This is where MonoHash saves the day. It allows developers to define different hash plans in relation to project's dependencies inside the monorepo. 

MonoHash is useful even for single projects repositories because it allows for easily defining the border between runtime code and test code - this means that CI/CD can cache a build if only the tests have changed, and only compile the tests again (for that single project).

MonoHash is fast. Running on a cold JVM via the `java -jar monohash.jar` on the checked out [Linux repository](https://github.com/torvalds/linux), hosted on a traditional HDD produces the following output:
```
[info] Hashed 63,120 files with a total of 812,456,520 bytes in 6.569 sec (average speed: 9,608 files/sec, 118.0 MB/sec)
```

## Usage

MonoHash relies on developers manually defining `.monohash` hash plans, one per project (or caching target) and listing dependencies by both whitelisting and blacklisting directories and files.  
Auxiliary goals of MonoHash are to also enable developers to optimise local caching by using it inside their build tools in a library fashion.

Let's look into an example frontend project `foo` hash plan and understand the file structure of the example monorepo:

```
/foo - frontend project
/bar - backend project
/build - CI/CD tools and build definitions
/resources - some resources shared across both projects
Jenkinsfile - root Jenkins definition
```

With the assumption that foo's `.monohash` file resides within the `/build/foo/.monohash` path, the hash plan could look something like this:

```
@../../foo/

../Jenkinsfile
../resources/

./

!.build/
!.vscode/
!dist/
!docs/
!mock/
!node_modules/

!yarn-error.log
```

There are three control characters in .monohash hash plan:  
`@` - defines the relative paths for all the following instructions  
`!` - negates a pattern, blacklisting traversal into that folder or particular file / pattern  
`#` - used for comments

Since some directories/files may start with these control characters, you can escape the control characters by prefixing them with a single backslash. Since the control characters need to be the first character on a line of that file, escaping is not necessary if they occur in other parts of the hash plan.

The `@` path at the top defines the absolute base path for the rest of the hash plan.  
It is relative to the physical location of the hash plan currently being parsed.  
There can be only one base path, and listing multiple (different) base paths will not work.

All lines that do not start with `!` form a whitelist of directories and files that MonoHash will traverse through.  
All lines that point to directories should end with a trailing `/`, for better visibility.  
Listing a directory without a `/` will still work, but will raise a warning in the logs.

Blacklist entries work by constraining the traversal path of the whitelist to exclude folders or files which should not affect the hash result.  
Blacklists can contain wildcards `*` that accept any number of characters (0 or more).

## Library and command line usage

MonoHash hash plans do not need to be named `.monohash`, e.g. to support multiple hash plans in the root of the monorepo - we're only using this name as a convention.  
Everything in `.monohash` hash plans is optional, following convention over configuration.  
A completely empty `.monohash` file simply includes all files in the folder of that hash plan.

There is an comprehensive set of tests and `.monohash` examples in the [oradian/monohash repository](https://github.com/oradian/monohash/tree/master/src/test/resources) which can be observed for education purposes.  

MonoHash is written in pure Java with *no external dependencies* so its binary payload is tiny (< 30kb).
It's able to integrate seamlessly into any type of JVM scripts (a'la Scala/Groovy), while easily being invokable from other build tools such as Yarn via command line utility.  


### Library dependency

**MonoHash** is being published to OSSRH / Maven Central and should be available without having to add additional repositories.  

Maven:
```
<dependency>
  <groupId>com.oradian.infra</groupId>
  <artifactId>monohash</artifactId>
  <version>0.5.0</version>
</dependency>
```

Ivy:
```
<dependency org="com.oradian.infra" name="monohash" rev="0.5.0"/>
```

SBT:
```
libraryDependencies += "com.oradian.infra" % "monohash" % "0.5.0"
```

### Command-line usage:

The MonoHash library can be used as a library, or ran on the command line.  
Running it on the command line allows for configuring hashing algorithms, concurrency and log levels:  


```
Usage: java -jar monohash.jar [hash plan file] [export file (optional)]

Additional options:
  -l <log level> (default: info, allowed values: off, error, warn, info, debug, trace)
  -a <algorithm> (default: SHA-1, some allowed values: MD2, MD5, SHA (aliases: SHA-1, SHA1), SHA-224, SHA-256, SHA-384, SHA-512)
  -c <concurrency> (default: 8 - taken from number of CPUs, unless specified here)
  -- stops processing arguments to allow for filenames which may conflict with options above
```

The optional `[export file]` argument is used to dump the hashes for each traversed file.  
Running MonoHash on its own folder produces the following export file: 
```
df09551082b426d7cd20fe1c94bcb98e38bc954f .travis.yml
3b6a875dbccac303ba946f8a5931dfae2896090d LICENSE
4f839fd5bf5a4359c341674657bc86fcec0e37d0 README.md
15f70682bf83413b2b69dd448c7c8c0c9900f61f build.sbt
6c821dd05062033149c61f082cd29b95e2b32edd project/build.properties
d6c27568ac6cbc2476d121bfbd9a3661a8b22b69 project/build.sbt
46edd497a7dd09e1aa4ca046eabc985496e21982 project/publish.sbt
1df2c4b2c189ba2b0e9d21199365ea1bf794575a publish.sbt
89de70be8804c4af71c2a02cc0d32588d8600933 src/main/java/com/oradian/infra/monohash/CmdLineParser.java
2e9285e35f7eb1969c6edc99e64dee5baa5e7138 src/main/java/com/oradian/infra/monohash/HashPlan.java
2e52f68404f90ec10d2709609871b31dd7d0413e src/main/java/com/oradian/infra/monohash/HashResults.java
679f015a635161c591d3244060109a2ee61a8c2e src/main/java/com/oradian/infra/monohash/HashWorker.java
...
```

The export file's checksum is the actual output from MonoHash. The file paths are sorted alphabetically and are absolute to the relative location specified in the hash plan.  
If no export file has been provided, MonoHash will calculate the hash in memory. 

## License

**MonoHash** is published under the MIT open source license.  

Contributions are more than welcome!
