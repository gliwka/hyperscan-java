# hyperscan-java
[![Maven Central](https://img.shields.io/maven-central/v/com.gliwka.hyperscan/hyperscan.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.gliwka.hyperscan%22%20a%3A%22hyperscan%22)
![example workflow name](https://github.com/gliwka/hyperscan-java/workflows/Java%20CI/badge.svg)



[hyperscan](https://github.com/intel/hyperscan) is a high-performance multiple regex matching library.

It uses hybrid automata techniques to allow simultaneous matching of large numbers (up to tens of thousands) of regular expressions and for the matching of regular expressions across streams of data.

This project is a third-party developed wrapper for the [hyperscan](https://github.com/intel/hyperscan) project to enable developers to integrate hyperscan in their java (JVM) based projects.

Because the latest hyperscan release is now under a [proprietary license](https://github.com/intel/hyperscan/issues/421) and ARM-support has never been integrated, this project utilizes the [vectorscan](https://github.com/VectorCamp/vectorscan) fork.

## Add it to your project
This project is available on maven central. 

The version number consists of two parts (i.e. 5.4.11-3.0.0).
The first part specifies the vectorscan version (5.4.11), the second part the version of this library utilizing semantic versioning
(3.0.0).

#### Maven
```xml
<dependency>
    <groupId>com.gliwka.hyperscan</groupId>
    <artifactId>hyperscan</artifactId>
    <version>5.4.11-3.0.0</version>
</dependency>
```

#### Gradle

```gradle
compile group: 'com.gliwka.hyperscan', name: 'hyperscan', version: '5.4.11-3.0.0'
```

#### sbt
```sbt
libraryDependencies += "com.gliwka.hyperscan" %% "hyperscan" % "5.4.11-3.0.0"
```

## Usage
If you want to utilize the whole power of the Java Regex API / full PCRE syntax
and are fine with sacrificing some performance, use the```PatternFilter```.
It takes a large lists of ```java.util.regex.Pattern``` and uses hyperscan
to filter it down to a few Patterns with a high probability that they will match.
You can then use the regular Java API to confirm those matches. This is similar to
chimera, only using the standard Java API instead of libpcre.

If you need the highest performance, you should use the hyperscan API directly.
Be aware, that only a smaller subset of the PCRE syntax is supported.
Missing features are for example backreferences, capture groups and backtracking verbs.
The matching behaviour is also a litte bit different, see [the semantics chapter](https://intel.github.io/hyperscan/dev-reference/compilation.html#semantics) of the hyperscan docs.

## Examples

### Use of the PatternFilter
```java
List<Pattern> patterns = asList(
        Pattern.compile("The number is ([0-9]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("The color is (blue|red|orange)")
        // and thousands more
);

//not thread-safe, create per thread
PatternFilter filter = new PatternFilter(patterns);

//this list now only contains the probably matching patterns, in this case the first one
List<Matcher> matchers = filter.filter("The number is 7 the NUMber is 27");

//now we use the regular java regex api to check for matches - this is not hyperscan specific
for(Matcher matcher : matchers) {
    while (matcher.find()) {
        // will print 7 and 27
        System.out.println(matcher.group(1));
    }
}
```


### Direct use of hyperscan
```java
import com.gliwka.hyperscan.wrapper;

...

//we define a list containing all of our expressions
LinkedList<Expression> expressions = new LinkedList<Expression>();

//the first argument in the constructor is the regular pattern, the latter one is a expression flag
//make sure you read the original hyperscan documentation to learn more about flags
//or browse the ExpressionFlag.java in this repo.
expressions.add(new Expression("[0-9]{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST)));
expressions.add(new Expression("Test", ExpressionFlag.CASELESS));


//we precompile the expression into a database.
//you can compile single expression instances or lists of expressions

//since we're interacting with native handles always use try-with-resources or call the close method after use
try(Database db = Database.compile(expressions)) {
    //initialize scanner - not thread-safe, so one scanner per concurrent thread!
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
catch(IOException ie) {
  //IO during serializing / deserializing failed
}
```


## Native libraries
This library ships with pre-compiled vectorscan binaries for linux (glibc >=2.17) and macOS for x86_64 and arm64 CPUs. 

Windows is no longer supported (last supported version is `5.4.0-2.0.0`) due to vectorscan dropping windows support.

You can find the repository with the native libraries [here](https://github.com/gliwka/hyperscan-java-native)

## Documentation
The [developer reference](https://intel.github.io/hyperscan/dev-reference/) explains vectorscan.
The javadoc is located [here](https://gliwka.github.io/hyperscan-java/).

## Changelog
[See here](CHANGELOG.md).

## Contributing
 Feel free to raise issues or submit a pull request.

## Credits
Shoutout to [@eliaslevy](https://github.com/eliaslevy), [@krzysztofzienkiewicz](https://github.com/krzysztofzienkiewicz), [@swapnilnawale](https://github.com/swapnilnawale), [@mmimica](https://github.com/mmimica) and [@Jiar](https://github.com/Jiar) for all the great contributions.

Thanks to Intel for opensourcing hyperscan and [@VectorCamp](https://github.com/VectorCamp) for actively maintaining the fork!

## License
[BSD 3-Clause License](LICENSE)

