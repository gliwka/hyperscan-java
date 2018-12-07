# hyperscan-java
[![Maven Central](https://img.shields.io/maven-central/v/com.gliwka.hyperscan/hyperscan.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.gliwka.hyperscan%22%20a%3A%22hyperscan%22)
[![Build Status](https://travis-ci.org/LocateTech/hyperscan-java.svg?branch=master)](https://travis-ci.org/LocateTech/hyperscan-java)
[![codecov](https://codecov.io/gh/LocateTech/hyperscan-java/branch/develop/graph/badge.svg)](https://codecov.io/gh/LocateTech/hyperscan-java)
[![Code Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.gliwka.hyperscan%3Ahyperscan&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.gliwka.hyperscan%3Ahyperscan)



[hyperscan](https://github.com/intel/hyperscan) is a high-performance multiple regex matching library.

It uses hybrid automata techniques to allow simultaneous matching of large numbers (up to tens of thousands) of regular expressions and for the matching of regular expressions across streams of data.


This project is a third-party developed JNA based java wrapper for the [hyperscan](https://github.com/intel/hyperscan) project to enable developers to integrate hyperscan in their java (JVM) based projects.

## Add it to your project
This project is available on maven central.

#### Maven
```xml
<dependency>
    <groupId>com.gliwka.hyperscan</groupId>
    <artifactId>hyperscan</artifactId>
    <version>0.6.2</version>
</dependency
```

#### Gradle

```gradle
compile group: 'com.gliwka.hyperscan', name: 'hyperscan', version: '0.6.2'
```

#### sbt
```sbt
libraryDependencies += "com.gliwka.hyperscan" %% "hyperscan" % "0.6.2"
```

## Simple example
```java
import com.gliwka.hyperscan.wrapper;

...

//we define a list containing all of our expressions
LinkedList<Expression> expressions = new LinkedList<Expression>();

//the first argument in the constructor is the regular pattern, the latter one is a expression flag
//make sure you read the original hyperscan documentation to learn more about flags
//or browse the ExpressionFlag.java in this repo.
expressions.add(new Expression("[0-9]{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST)));
expressions.add(new Expression("Test", EnumSet.of(ExpressionFlag.CASELESS)));


//we precompile the expression into a database.
//you can compile single expression instances or lists of expressions

//since we're interacting with native handles always use try-with-resources or call the close method after use
try(Database db = Database.compile(expressions)) {
    //initialize scanner - one scanner per thread!
    //same here, always use try-with-resources or call the close method after use
    try(Scanner scanner = new Scanner())
    {
        //allocate scratch space matching the passed database
        scanner.allocScratch(db);

        
        //provide the database and the input string
        //returns a list with matches
        //synchronized method, only one execution at a time (use more scanner instances for multithreading)
        List<Match> matches = scanner.scan(db, "12345 test string");

        //matches always contain the expression causing the match and the end position of the match
        //the start position and the matches string it self is only part of a matach if the
        //SOM_LEFTMOST is set (for more details refer to the original hyperscan documentation)
    }

    // Save the database to the file system for later use
    try(OutputStream out = new FileOutputStream("db")) {
        db.save(out);
    }

    // Later, load the database back in. This is useful for large databases that take a long time to compile.
    // You can compile them offline, save them to a file, and then quickly load them in at runtime.
    // The load has to happen on the same type of platform as the save.
    try (InputStream in = new FileInputStream("db");
         Database loadedDb = Database.load(in)) {
        // Use the loadedDb as before.
    }
}
catch (CompileErrorException ce) {
    //gets thrown during  compile in case something with the expression is wrong
    //you can retrieve the expression causing the exception like this:
    Expression failedExpression = ce.getFailedExpression();
}
catch(Throwable e) {
    //edge cases like OOM, illegal platform etc.
}
}
```


## Limitations of hyperscan-java

It currently works on OS X and Linux, only! We currently only ship precompiled binaries for 64-bit (x86_64).

hyperscan only supports a subset of regular expressions. Notable exceptions are for example backreferences and capture groups. Please read the [hyperscan developer reference](https://intel.github.io/hyperscan/dev-reference/) so you get a good unterstanding how hyperscan works and what the limitations are.

hyperscan will only run on x86 processors in 64-bit and 32-bit modes and takes advantage of special instruction sets, when available. Check the original [project documentation](https://intel.github.io/hyperscan/dev-reference/getting_started.html#hardware) to learn more.

## Dependencies
This wrapper only on the hyperscan shared library for it's functionality. It already contains precompiled 64-bit shared libraries for macOS and 64-bit libraries for most glibc (>=2.17) based Linux distributions.

#### Compile yourself
In case the precompiled binaries don't suite your usecase or architecture, you got to 
make sure you've got hyperscan compiled as a shared library on your system. On Linux and macOS a ```mkdir build && cd build && cmake -DBUILD_SHARED_LIBS=YES .. && make``` inside the git repositiory was enough. For more information about how to compile hyperscan visit the [project documentation](https://intel.github.io/hyperscan/dev-reference/).

Make sure you specify the system property ```jna.library.path``` using code or the command line to point to a location which includes the hyperscan shared libraries.


## Javadoc

The javadoc is located [here](https://LocateTech.github.io/hyperscan-java/).



## Included packages
```com.gliwka.hyperscan.wrapper``` provides a java-style wrapper arround hyperscan.
```com.gliwka.hyperscan.jna``` implements the JNA interface and primitives used to call the hyperscan c library.

If you just want to use hyperscan in java, you only need to import ```com.gliwka.hyperscan.wrapper```.


## Currently not implemented
 * Serialization and Deserialization of databases
 * Extended expression syntax using [hs_compile_ext_multi()](http://intel.github.io/hyperscan/dev-reference/api_files.html#project0hs__compile_8h_1aacc508bea3042f1faba32c3818bfc2a3)


## Contributing
 Feel free to raise issues or submit a pull request.


## Version control
 This project follows the [git flow](https://github.com/kashike/flow-nbt/blob/master/README.md) branching model.
 All development happens on feature branches. ```develop``` is the integration branch. The ```master``` branch only contains production-ready code. All releases are tagged.

## Credits
Shoutout to [@eliaslevy](https://github.com/eliaslevy) for all the great contributions and to the hyperscan team for this great library!

## License
[BSD 3-Clause License](LICENSE)

