# MonoHash
[![Build Status](https://travis-ci.org/oradian/monohash.svg?branch=master)](https://travis-ci.org/oradian/monohash)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.oradian.infra/monohash/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.oradian.infra/monohash)
[![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)](https://opensource.org/licenses/MIT)
[![Codacy](https://app.codacy.com/project/badge/Grade/2c1989ff20904033b7369cb50d9c6e38)](https://www.codacy.com/gh/oradian/monohash/dashboard)
[![Codecov](https://codecov.io/gh/oradian/monohash/branch/master/graph/badge.svg)](https://codecov.io/gh/oradian/monohash)

**MonoHash** is a hashing library designed to work with monorepos containing multiple projects.  
It's primary purpose is to allow for lean CI/CD cache invalidation which will only build relevant changes while ignoring
projects which are impervious to these particular changes.


## Rationale

Having a single monorepo-level hash, such as Git's commit hashes are not sufficiently fine-grained for the purposes of
caching, as any change to the documentation files, .gitignores, or files that are not relevant to the project we intend
to cache will invalidate that single hash - which means all projects will need to be rebuilt.

This is where **MonoHash** saves the day. It allows developers to define different hash plans in relation to project's
dependencies inside the monorepo.

MonoHash is useful even for single projects repositories because it allows for easily defining the border between
runtime code and test code - this means that your CI can reuse a cached build if only the tests have changed, and only
run the test part of that CI pipeline while keeping the previously built main artifacts.

MonoHash is **fast**. Running on a cold JVM via the `java -jar monohash.jar` on the
[Linux repository](https://github.com/torvalds/linux/releases/tag/v5.10-rc5) completes in under a second when running on
[Hetzner's PX line](https://www.hetzner.com/dedicated-rootserver/px62-nvme):
```
[melezov@ci-01 monohash]$ sbt package
[info] welcome to sbt 1.4.3 (AdoptOpenJDK Java 1.8.0_272)
[info] set current project to monohash (in build file:/home/melezov/monohash/)
[info] compiling 20 Java sources to /home/melezov/monohash/target/classes ...
[success] Total time: 2 s, completed Nov 30, 2020 12:39:43 AM

[melezov@ci-01 monohash]$ java -jar target/monohash-0.8.0.jar ~/linux-5.10-rc5/
[info] Using [hash plan directory]: '/home/melezov/linux-5.10-rc5/' ...
[info] Hashed 74,822 files with a total of 989,045,424 bytes in 0.792 sec (average speed: 94,472 files/sec, 1,190 MiB/sec)
[info] Executed hash plan by hashing 74,822 files [9be032657c49009add50c4fbccd1d6bffaa4d593] (in 0.917 sec)
```


## Usage

MonoHash runs against `.monohash` hash plans, one per project (or caching target).  
The hash plans define targets by both whitelisting and blacklisting directories and files.  
Auxiliary goals of MonoHash are to also enable developers to optimise local caching by using it inside their build tools
in a library fashion.

Let's look into an example frontend project `foo` hash plan and understand the file structure of the example monorepo:

```
/foo - frontend project
/bar - backend project
/build - CI/CD tools and build definitions
/resources - some resources shared across both projects
/Jenkinsfile - root Jenkins definition
```

With the assumption that foo's `.monohash` file resides within the `/build/foo/.monohash` path, the hash plan could look
something like this:

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

In plain English, this hash plan instructs MonoHash to perform the following:
1) Position yourself into the `/foo` frontend project, and consider this as the anchor for all future directives
2) Include all the files inside the `/resources` folder, and also use the `Jenkinsfile` in the root of the repository
3) Now also include all the files in the actual `/foo` directory ...
4) With the exception of the blacklisted entries such as `node_modules/` or `docs/` directories

Now, to elaborate on the hash plan:

There are three control characters in .monohash hash plan:  
`@` - defines the relative paths for all the following instructions  
`!` - negates a pattern, blacklisting traversal into that folder or particular file / pattern  
`#` - used for comments

Since some directories/files may start with these control characters, you can escape the control characters by prefixing
them with a single backslash. Since the control characters need to be the first character on a line of that file,
escaping is not necessary if they occur in other parts of the hash plan.

The `@` path at the top defines the absolute base path for the rest of the hash plan.  
It is relative to the physical location of the hash plan currently being parsed.  
There can be only one base path, and listing multiple (different) base paths will not work.

All lines that do not start with `!` form a whitelist of directories and files that MonoHash will traverse through.  
All lines that point to directories should end with a trailing `/`, for better visibility.  
Listing a directory without a `/` will still work, but will raise a warning in the logs.

Blacklist entries work by constraining the traversal path of the whitelist to exclude folders or files which should not
affect the hash result.  
Blacklists can contain wildcards `*` that accept any number of characters (0 or more).


## Library and command line usage

MonoHash hash plans do not need to be named `.monohash`, e.g. to support multiple hash plans in the root of the
monorepo - we're only using this name as a convention.  
Everything in `.monohash` hash plans is optional, following convention over configuration.  
A completely empty `.monohash` file simply includes all files in the folder of that hash plan.

There is a comprehensive set of tests and `.monohash` examples in the
[oradian/monohash repository](https://github.com/oradian/monohash/tree/master/src/test/resources) which can be observed
for education purposes.

MonoHash is written in pure Java with *no external dependencies* so its binary payload is tiny (~45kb).
It's able to integrate seamlessly into any type of JVM scripts (a'la Scala/Groovy), while easily being invokable from
other build tools such as Yarn via command line utility.

### Library dependency

**MonoHash** is being published to OSSRH / Maven Central and should be available without having to add additional
repositories.

Maven:
```
<dependency>
  <groupId>com.oradian.infra</groupId>
  <artifactId>monohash</artifactId>
  <version>0.8.0</version>
</dependency>
```

Ivy:
```
<dependency org="com.oradian.infra" name="monohash" rev="0.8.0"/>
```

SBT:
```
libraryDependencies += "com.oradian.infra" % "monohash" % "0.8.0"
```

### Command-line usage:

If you don't care about programmatic (library) access, you can simply
[download the binary](https://oss.sonatype.org/content/groups/public/com/oradian/infra/monohash/0.8.0/monohash-0.8.0.jar)
and use it on the command line.

Running it on the command line allows for some configuration such as choosing the hashing algorithm, concurrency and log levels:

```
Usage: java -jar monohash.jar <options> [hash plan file] [export file (optional)]

Options:
  -l <log level> (default: info, allowed values: off, error, warn, info, debug, trace)
  -a <algorithm> (default: SHA-1, some allowed values: GIT, MD2, MD5, SHA-1, SHA-224, SHA-256, SHA-384, SHA-512, SHA-512/224, SHA-512/256, SHA3-224, SHA3-256, SHA3-384, SHA3-512)
  -c <concurrency> (default: 8 - taken from number of CPUs)
  -v <verification> (default: off, allowed values: off, warn, require)
  -- stops parsing options to allow for filenames which may conflict with options above
```

The optional `[export file]` argument can be used to dump the hashes for each traversed file.  
Running MonoHash on its own folder produces the following export file:
```
81c58d80a333caf6c8e077e809f52755c4bf7b0d .monohash
1a98da71f224d591991ca4a13663c381a404eede .travis.yml
e91e0a6e2ae28daabd36016589464890e988418e build.sbt
aac1c402f5ba623eefcdf2f1c60258defff1973f project/build.properties
6abd00785fa263b088b1cb1457270418a14fc5bf project/plugins.sbt
bc5c5f56be234577c861a00de3b9a6b52bcd58e4 project/publish.sbt.disabled
8be7a61b089e8250049887b3954037fb8833b13a publish.sbt.disabled
229bbaf4041633f809bf3784a174e7f9c6fc9589 src/main/java/com/oradian/infra/monohash/Algorithm.java
ae0181ec4c07e7d77789d8a66f4d4a2490ebe226 src/main/java/com/oradian/infra/monohash/CmdLineParser.java
81197abdab8214610c2dab552dd4ceb1cdfaeeda src/main/java/com/oradian/infra/monohash/ExitException.java
818b9e83e0a8e2cfbfc5783d28697957be9c8a52 src/main/java/com/oradian/infra/monohash/ExportParsingException.java
b9ab413b5242698be57a2c5c947731d724ee4488 src/main/java/com/oradian/infra/monohash/HashPlan.java
659f65aac9e6054ef762fa3d955311749f883421 src/main/java/com/oradian/infra/monohash/HashResults.java
44b3176c9f64dfeaea81ddccc46006b9dc7e737a src/main/java/com/oradian/infra/monohash/HashWorker.java
...
```

The export file's checksum is the actual output from MonoHash. The file paths are sorted alphabetically and are absolute
to the relative location specified in the hash plan.  
If no export file has been provided, MonoHash will calculate the hash in memory.

#### Running without hash plans

It is possible to run MonoHash against a project without specifying a hash plan by targeting the project's top-level
directory instead. MonoHash will behave as if you have ran it against a completely empty `.monohash` hash plan in that
directory, by adding every directory and file to the whitelist and not excluding anything.

Take into consideration that this is probably not something that will be useful if you have folders such as `.git`,
since this will invalidate hashes due to subtle differences in the way the underlying Git database is packaged.  
You usually want to exclude your VCS-specific files and directories as the first thing when specifying a hash plan.

#### Additional options

- `-l <log level>` allows you to see what's going on under the hood, or completely squelch the log output.  
By default, MonoHash will output the log to stderr.  
When using MonoHash programmatically, you can bridge logging into your favourite flavour such as SLF4J.

- `-a <algorithm>` can be used to override the default digest algorithm (`SHA-1`). MonoHash will happily use faster
algorithms such as `MD5`, or heavier stuff such as `SHA3-512`. Probably overkill, but the choice is yours.  
The supported algorithms are gathered by querying registered security providers, i.e. they will depend on the JRE
version that's running MonoHash and if you have loaded external providers such as
[Bouncy Castle](https://www.bouncycastle.org/).  
  An additional synthetic algorithm `Git` is made available by MonoHash. It depends on `SHA-1` and allows you to use
  hashing compatible with [Git's object IDs](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects) - i.e. it first
  processes the `"blob ${length}\0"` prefix and then proceeds with hashing the rest of the file.

- `-c <concurrency>` will by default query the number of available processors, and can be overridden with a positive
integer. The work is both IO (reading) and CPU bound, depending on the digest algorithm used.

- `-v <verification>` allows you to diff existing MonoHash export files against the one that will be calculated.
  - `off` completely ignores the existence of the previous export and simply overwrites it
  - `warn` will read the previous export file (if it exists) and log the diff with a `warn` log level if there were any
  changes.  
  This verification option is useful when you want to ensure that you did not forget to blacklist a part of the project,
  and is a good default when doing local development.
  - `require` will require that the previous export file exists and abort operations otherwise. If the export file was
  different to the current calculation it will log the diff with an `error` log level and abort the operation - without
  overwriting the previous file.  
  The `require` verification is a good default for CI operations - e.g. you can run it before and after finishing a
  build to ensure that the export didn't mutate due to non-blacklisted items.


## License

**MonoHash** is published under the MIT open source license.

Contributions are more than welcome!
