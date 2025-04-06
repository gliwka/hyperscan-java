# hyperscan-java
[![Maven Central](https://img.shields.io/maven-central/v/com.gliwka.hyperscan/hyperscan.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.gliwka.hyperscan%22%20a%3A%22hyperscan%22)
![Java CI](https://github.com/gliwka/hyperscan-java/workflows/Java%20CI/badge.svg)

## Overview

Hyperscan-java provides Java bindings for [Vectorscan](https://github.com/VectorCamp/vectorscan), a fork of Intel's [Hyperscan](https://github.com/intel/hyperscan) - a high-performance multiple regex matching library.

Vectorscan uses hybrid automata techniques to allow simultaneous matching of large numbers (up to tens of thousands) of regular expressions across streams of data with exceptional performance.

### Key Features

- **High Performance**: Scan text against thousands of patterns simultaneously with high performance
- **Two Usage Modes**:
  - Direct Vectorscan API for maximum performance (with limited regex syntax support)
  - `PatternFilter` utility for full Java Regex API compatibility
- **UTF-8 Support**: Proper handling of Unicode text with character-based matching results
- **Cross-Platform**: Pre-compiled native libraries for Linux and macOS (x86_64 and arm64)
- **Database Serialization**: Save and load compiled pattern databases to avoid recompilation costs

## Installation

The library is available on Maven Central. The version number consists of two parts (e.g., `5.4.11-3.1.0`):
- First part: Vectorscan version (`5.4.11`)
- Second part: Library version using semantic versioning (`3.1.0`)

### Maven
```xml
<dependency>
    <groupId>com.gliwka.hyperscan</groupId>
    <artifactId>hyperscan</artifactId>
    <version>5.4.11-3.1.0</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.gliwka.hyperscan:hyperscan:5.4.11-3.1.0'
```

### SBT
```sbt
libraryDependencies += "com.gliwka.hyperscan" %% "hyperscan" % "5.4.11-3.1.0"
```

## Usage Options

The library offers two primary ways to use the regex matching capabilities:

### 1. Using PatternFilter (Recommended for most cases)

`PatternFilter` is ideal when you:
- Need full Java Regex API functionality and PCRE syntax
- Want to pre-filter a large list of patterns efficiently
- Need capture groups, backreferences, or other advanced regex features

It uses Vectorscan to quickly identify potential matches, then confirms them with Java's standard Regex engine.

### 2. Direct Vectorscan API (For maximum performance)

Use the direct API when:
- You need the absolute highest performance
- Your patterns work within Vectorscan's [supported syntax](https://intel.github.io/hyperscan/dev-reference/compilation.html#pattern-support)
- You understand the differences in matching semantics

Note: Vectorscan doesn't support backreferences, capture groups, or backtracking verbs.

## Examples

### Using PatternFilter

```java
import com.gliwka.hyperscan.util.PatternFilter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.Arrays.asList;

public class PatternFilterExample {
    public static void main(String[] args) {
        // Create a list of Java regex patterns (could be thousands)
        List<Pattern> patterns = asList(
            Pattern.compile("The number is ([0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("The color is (blue|red|orange)"),
            Pattern.compile("\\w+@\\w+\\.com")
            // imagine thousands more patterns here
        );

        try (PatternFilter filter = new PatternFilter(patterns)) {
            String text = "The number is 42 and the NUMBER is 123. Contact info@example.com";
            
            // Quickly filter to just the potentially matching patterns
            List<Matcher> potentialMatches = filter.filter(text);
            
            // Use Java's Regex API to confirm matches and extract groups
            for (Matcher matcher : potentialMatches) {
                while (matcher.find()) {
                    System.out.println("Pattern: " + matcher.pattern());
                    System.out.println("Match: " + matcher.group(0));
                    
                    // Handle capture groups if present
                    if (matcher.groupCount() > 0) {
                        System.out.println("Captured: " + matcher.group(1));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Using Direct Vectorscan API

```java
import com.gliwka.hyperscan.wrapper.*;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class DirectHyperscanExample {
    public static void main(String[] args) {
        // Define expressions to match
        LinkedList<Expression> expressions = new LinkedList<>();
        
        // Expression(pattern, flags, id)
        expressions.add(new Expression("[0-9]{5}", EnumSet.of(ExpressionFlag.SOM_LEFTMOST), 0));
        expressions.add(new Expression("test", EnumSet.of(ExpressionFlag.CASELESS), 1));
        expressions.add(new Expression("example\\.(com|org|net)", EnumSet.of(ExpressionFlag.SOM_LEFTMOST), 2));

        try (Database db = Database.compile(expressions)) {
            try (Scanner scanner = new Scanner()) {
                // Allocate scratch space matching the database
                scanner.allocScratch(db);
                
                // Scan text against all patterns simultaneously
                String text = "12345 is a zip code. Test this at example.com!";
                List<Match> matches = scanner.scan(db, text);
                
                for (Match match : matches) {
                    Expression matchedExpression = match.getMatchedExpression();
                    
                    System.out.println("Pattern: " + matchedExpression.getExpression());
                    System.out.println("Pattern ID: " + matchedExpression.getId());
                    
                    // Note: start and end positions are character indices (inclusive)
                    System.out.println("Start position: " + match.getStartPosition());
                    System.out.println("End position: " + match.getEndPosition());
                    
                    // The matched string is only available if SOM_LEFTMOST flag was used
                    if (matchedExpression.getFlags().contains(ExpressionFlag.SOM_LEFTMOST)) {
                        System.out.println("Matched text: " + match.getMatchedString());
                    }
                    
                    System.out.println("---");
                }
                
                // You can also check if a pattern matches without getting match details
                boolean hasAnyMatch = scanner.hasMatch(db, text);
                System.out.println("Has any match: " + hasAnyMatch);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Pattern Validation and Expression Flags

```java
import com.gliwka.hyperscan.wrapper.*;
import java.util.EnumSet;

public class ValidationExample {
    public static void main(String[] args) {
        // Create an expression to validate
        Expression expr = new Expression(
            "a++", // This pattern uses a feature not supported by Hyperscan
            EnumSet.of(ExpressionFlag.UTF8)
        );
        
        // Check if the expression is valid for Hyperscan
        Expression.ValidationResult result = expr.validate();
        
        if (result.isValid()) {
            System.out.println("Pattern is valid");
        } else {
            System.out.println("Pattern is invalid: " + result.getErrorMessage());
        }
        
        // Common expression flags:
        // - ExpressionFlag.CASELESS - Case insensitive matching
        // - ExpressionFlag.DOTALL - Dot (.) matches newlines
        // - ExpressionFlag.MULTILINE - ^ and $ match on line boundaries
        // - ExpressionFlag.UTF8 - Pattern and input are UTF-8
        // - ExpressionFlag.SOM_LEFTMOST - Track start of match (enables getMatchedString())
        // - ExpressionFlag.PREFILTER - Optimize for pre-filtering (used by PatternFilter)
    }
}
```

### Saving and Loading Compiled Databases

```java
import com.gliwka.hyperscan.wrapper.*;
import java.io.*;
import java.util.EnumSet;

public class DatabaseSerializationExample {
    public static void main(String[] args) {
        try {
            // Create and compile a database
            Expression expr = new Expression("\\w+@\\w+\\.(com|org|net)", 
                EnumSet.of(ExpressionFlag.SOM_LEFTMOST));
            
            // Saving to a file
            try (Database db = Database.compile(expr);
                 OutputStream out = new FileOutputStream("email_patterns.db")) {
                db.save(out);
                System.out.println("Database saved, size: " + db.getSize() + " bytes");
            }
            
            // Loading from a file later (much faster than recompiling)
            try (InputStream in = new FileInputStream("email_patterns.db");
                 Database loadedDb = Database.load(in);
                 Scanner scanner = new Scanner()) {
                
                scanner.allocScratch(loadedDb);
                List<Match> matches = scanner.scan(loadedDb, "Contact us at info@example.com");
                
                System.out.println("Found " + matches.size() + " matches");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Important Implementation Notes

### Character vs Byte Positions

Vectorscan operates on bytes (exclusive end index), but `Scanner.scan()` returns `Match` objects with **inclusive** character indices:

- `getStartPosition()` - Character index where the match starts
- `getEndPosition()` - Character index where the match ends (inclusive)

This is especially important when working with UTF-8 text where byte and Java character positions differ. If you're not interested in Java characters, you might see some performance improvement in using a lower level API (see below).

### Thread Safety

- The native Vectorscan library is not thread-safe for the re-use of scratch spaces. Those are encapsulated in Scanners
- Create separate `Scanner` (scratch space) instances for each thread
- `Database` instances are thread-safe for scanning
- Always use try-with-resources or explicitly call `close()` on `Scanner` and `Database` instances

### Callback Handlers and Byte-Oriented Scanning

In addition to the default scanning methods that return `Match` objects, hyperscan-java provides callback-based scanning methods for more efficient processing:

#### Callback-Based Scan Methods

1. **String-based scanning with callbacks**:
   ```java
   void scan(Database db, String input, StringMatchEventHandler eventHandler)
   ```
   - Handles UTF-8 character to byte mapping automatically
   - Invokes your callback with character indices (inclusive start and end)
   - Return `false` from callback to stop scanning early

2. **Byte-oriented scanning with callbacks**:
   ```java
   void scan(Database db, byte[] input, ByteMatchEventHandler eventHandler)
   void scan(Database db, ByteBuffer input, ByteMatchEventHandler eventHandler)
   ```
   - Works directly with raw bytes for maximum performance
   - Avoids UTF-8 to character mapping overhead
   - Provides byte offsets to callback (inclusive start, exclusive end)
   - Ideal for binary data or when you need to handle byte offsets yourself

#### Example Using Callback-Based Scanning

```java
// String-based scanning with callback
scanner.scan(db, inputString, (expression, fromIndex, toIndex) -> {
    System.out.printf("Match for pattern '%s' at positions %d-%d: %s%n", 
        expression.getExpression(),
        fromIndex,
        toIndex,
        inputString.substring((int)fromIndex, (int)toIndex + 1)); // +1 because toIndex is inclusive
    
    return true; // continue scanning
});

// Byte-oriented scanning with callback
byte[] inputBytes = "sample text".getBytes(StandardCharsets.UTF_8);
scanner.scan(db, inputBytes, (expression, fromByteIdx, toByteIdxExclusive) -> {
    System.out.printf("Match for pattern '%s' at byte positions %d-%d%n", 
        expression.getExpression(),
        fromByteIdx,
        toByteIdxExclusive - 1); // -1 to convert to inclusive index for display
    
    return true; // continue scanning
});
```

#### Performance Considerations

- Use byte-oriented methods when:
  - Working with binary data
  - Processing high volumes of data where UTF-8 conversion is a bottleneck
  - You want to avoid allocating `Match` objects for very frequent matches
  - Integrating with code that already works with byte offsets

- Use string-oriented methods when:
  - Working with text data where character positions matter
  - You need to extract matched substrings
  - The convenience of character-based indices outweighs the performance benefit

## Platform Support

This library ships with native binaries for:
- Linux (glibc ≥2.17) - x86_64 and arm64
- macOS - x86_64 and arm64

Windows is no longer supported after version `5.4.0-2.0.0` due to Vectorscan dropping Windows support.

## Documentation

- [Hyperscan Developer Reference](https://intel.github.io/hyperscan/dev-reference/)
- [Changelog](CHANGELOG.md)

## Contributing

Feel free to raise issues or submit pull requests. Please see the native libraries repository [here](https://github.com/gliwka/hyperscan-java-native).

## Credits

Special thanks to [@eliaslevy](https://github.com/eliaslevy), [@krzysztofzienkiewicz](https://github.com/krzysztofzienkiewicz), [@swapnilnawale](https://github.com/swapnilnawale), [@mmimica](https://github.com/mmimica), [@Jiar](https://github.com/Jiar) and [@apismensky](https://github.com/apismensky) for their contributions.

Thanks to Intel for originally open-sourcing Hyperscan and [@VectorCamp](https://github.com/VectorCamp) for actively maintaining the Vectorscan fork!

## License

[BSD 3-Clause License](LICENSE)
