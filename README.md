# hyperscan-java
This project is a third-party developed JNA based java wrapper for the [hyperscan](https://github.com/01org/hyperscan) project.

The original project is developed by the the Intel Open Source Technology Center and describes it self as follows:

Hyperscan is a high-performance multiple regex matching library. It follows the regular expression syntax of the commonly-used libpcre library, but is a standalone library with its own C API.

Hyperscan uses hybrid automata techniques to allow simultaneous matching of large numbers (up to tens of thousands) of regular expressions and for the matching of regular expressions across streams of data.

(see https://github.com/01org/hyperscan)

## Prerequisites
Make sure you've got hyperscan compiled as a shared library on your system. On Linux a ```mkdir build && cd build && cmake -DBUILD_SHARED_LIBS=YES ..``` inside the git repositiory was enough. For more information about how to compile hyperscan visit the [project documentation](https://01org.github.io/hyperscan/dev-reference/).

Make sure you specify the system property ```jna.library.path``` using code or the command line to point to a location which includes the hyperscan shared libraries.

Hyperscan only supports a subset of regular expressions. Notable exceptions are for example backreferences and capture groups. Please read the [hyperscan developer reference](https://01org.github.io/hyperscan/dev-reference/) so you get a good unterstanding how hyperscan works and what the limitations are.

## Included packages
```com.gliwka.hyperscan.wrapper``` provides a java-style wrapper arround hyperscan.
```com.gliwka.hyperscan.jna``` implements the JNA interface and primitives used to call the hyperscan c library.

If you just want to use hyperscan in java, you only need to import ```com.gliwka.hyperscan.wrapper```.

## Add it to your project
Visit https://jitpack.io/#gliwka/hyperscan-java to add it to your project. Select the desired version and click on *Get it*. Then choose your build tool and follow the instructions. Gradle, maven, sbt and leiningen are supported.

Thanks to jitpack.io for hosting this project. 

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
try {
    Database db = Database.Compile(expressions);

    //initialize scanner
    Scanner scanner = new Scanner();
    
    //provide the database and the input string
    //returns a list with matches
    //synchronized method, only one execution at a time (use more scanner instances for multithreading)
    List<Match> matches = scanner.Scan(db, "12345 test string");

    //matches always contain the expression causing the match and the end position of the match
    //the start position and the matches string it self is only part of a matach if the
    //SOM_LEFTMOST is set (for more details refer to the original hyperscan documentation)
}
catch (CompileErrorException ce) {
    //gets thrown during  compile in case something with the expression is wrong
    //you can retrieve the expression causing the exception like this:
    Expression failedExpression = ce.getFailedExpression();
}
catch(Throwable e) {
    //edge cases like OOM, illegal platform etc.
}
```

## Currently not implemented
 * Serialization and Deserialization of databases
 * Extended expression syntax using [hs_compile_ext_multi()](http://01org.github.io/hyperscan/dev-reference/api_files.html#project0hs__compile_8h_1aacc508bea3042f1faba32c3818bfc2a3)

 Feel free to submit a pull request.

 ## License
 [BSD 3-Clause License](LICENSE)
