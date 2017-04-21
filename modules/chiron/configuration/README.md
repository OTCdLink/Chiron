Chiron-configuration
====================

Chiron-configuration is a Java library for reading configuration files and command-line arguments in the safest possible way. It use static typing and fail-fast wherever it can.

Chiron-configuration started as a conceptual spin-off of the [OWNER](https://github.com/lviggiano/owner) project. Annotations quickly showed their limits when defining default values or property constraints, which must be strings or classes instead of real objects. Chiron-configuration promotes a purely imperative approach.    

Chiron-configuration is tailored for usage in a closed-source project. So it's main purpose here on Github is to showcase a pair of coding ideas.

Chiron-configuration relies on Java 8 syntax.
 
Feel free to use and fork Chiron-configuration under the terms of the Gnu Public License, version 3.


Basic usage
-----------

Declare an interface like this:

```java
public interface Simple extends Configuration {
  int myNumber() ;
  String myString() ;
}
```

Create a `my.properties` file:

```properties
myString = Hello
myNumber = 43
```

Read the file and get an implementation:

```java
final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;
final Simple configuration = factory.create( Sources.newSource( new File( "my.properties" ) ) ;

assertThat( configuration.myNumber() ).isEqualTo( 43 ) ;
assertThat( configuration.myString() ).isEqualTo( "Hello" ) ;
assertThat( configuration.toString() ).isEqualTo( "Simple{myNumber=43; myString=Hello}" ) ;
```

Got the idea? There is more.

Features
--------

- Default values as statically typed Java objects.
- Fail-fast on unknown property names.
- Fail-fast on undefined properties.
- Custom property names.
- Bulk transformation of method names (`myName() -> 'my.name' or 'my-name'`).
- Default and custom converters to build objects from strings.
- Immutability wherever possible, using Guava's `Immutable*`. Chiron-configuration heavily relies on Guava.
- Sources can be files, or command-line parameters, or anything. 
- Property overriding when using multiple sources. So you can cascade several files and command-line parameters.
- Bulk validation (applies to a whole `Configuration` object).
- Validation may automatically capture last accessed properties to add their name to the message.
- Error messages with source location.
- Raises as many errors as possible in a single run, for batched corrections.
- Pattern-based obfuscation of the string representation of sensible parts (like passwords).
- Configuration object metadata (with the `Inspector`). 
- Automatic online help with property names, default values, and arbitrary text.
- Calculated properties using (typesafe) custom code .

The snippet below illustrates some of those features.

```java
final Configuration.Factory< Simple > factory ;
factory = new TemplateBasedFactory< Simple >( Simple.class ) {
  @Override
  protected void initialize() {
    property( using.myNumber() )
        .name( "my-binary-number" )
        .maybeNull()
        .converter( Converters.from( input -> Integer.parseInt( input, 2 ) ) )
        .documentation( "Just a number." )
    ;
    property( using.myString() )
        .defaultValue( "FOO" )
        .documentation( "Just a string." )
        .obfuscator( Pattern.compile( "OO" ) )
    ;
    setGlobalNameTransformer( NameTransformers.LOWER_HYPHEN ) ;
  }

  @Override
  protected ImmutableSet< Bad > validate( final Simple configuration ) {
    final Accumulator< Simple > accumulator = new Accumulator<>( configuration ) ;
    if( configuration.myNumber() != null ) {
      accumulator.verify( configuration.myNumber() > 0, "Must be > 0" ) ;
    }
    accumulator.verify(
        configuration.myString().equals( configuration.myString().toUpperCase() ),
        "Must be upper case"
    ) ;
    return accumulator.done() ;
  }
} ;

final Simple configuration = factory.create( Sources.newSource(
    "my-binary-number = 1111011" ) ) ;

final Inspector< Simple > inspector = ConfigurationTools.newInspector( configuration ) ;
assertThat( configuration.myNumber() ).isEqualTo( 123 ) ;
assertThat( inspector.usingDefault( inspector.lastAccessed().get( 0 ) ) ).isFalse() ;
assertThat( configuration.myString() ).isEqualTo( "FOO" ) ;
assertThat( inspector.usingDefault( inspector.lastAccessed().get( 0 ) ) ).isTrue() ;
assertThat( inspector.lastAccessed().get( 0 ).name() ).isEqualTo( "my-string" ) ;
assertThat( inspector.safeValueOf( inspector.lastAccessed().get( 0 ), "*" ) )
    .isEqualTo( "F*" ) ;
}

```

All the magic lies in `using` and `inspector` objects that capture a method call to designate the property to use immediately after. It's not different of what [Mockito](http://mockito.org) and other testing frameworks do. 

See [tests](https://github.com/OTCdLink/Chiron-framework/tree/master/modules/chiron/configuration/src/test/java/io/github/otcdlink/chiron/configuration/showcase) for more use cases.

Future
------

Things that Chiron-configuration will never do:
- Use annotations to define property behaviors.
- Mutate a `Configuration` object.
- Support some XML format (but it lets you implement your own `Source`).
- Try to get famous.

Things that it might do one day:
- Support nested `Configuration` objects.
- Offer more default `Converter`s.
