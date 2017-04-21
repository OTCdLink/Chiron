package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Method;
import java.util.Comparator;

/**
 * Tagging interface for interfaces defining a proxy-backed configuration.
 * Methods must have no parameter.
 * Each method represents a property, which has a
 * {@link Configuration.Property} and a value.
 *
 */
public interface Configuration {

  /**
   * Represents something a part of, or a whole {@link Configuration}.
   */
  interface Source {

    /**
     * Helps identifying the file or the code the values come from.
     */
    String sourceName() ;


    /**
     * Contains {@code String}s to
     * {@link Configuration.Converter#convert(String) convert}
     * into expected type.
     */
    interface Stringified extends Source {
      ImmutableMap< String, String > map() ;
    }

    /**
     * Contains property values as {@link Configuration} methods
     * should return them.
     */
    interface Raw< C extends Configuration > extends Source {
      ImmutableMap< Property< C >, Object > map() ;
    }

  }


  /**
   * Creates a {@link Configuration} object given one or more {@link Source}s.
   *
     */
  interface Factory< C extends Configuration > {

    /**
     * Values in last {@link Source} objects override those in first ones.
     */
    C create( Source source1, Source... others ) throws ConfigurationException ;

    Class< C > configurationClass() ;

    ImmutableMap< String, Property< C > > properties() ;
  }

  /**
   * Defines the behavior and the constraints of a property corresponding to a method
   * in a {@link Configuration}.
   */
  interface Property< C extends Configuration > {

    Method declaringMethod() ;

    String name() ;

    Class< ? > type() ;

    /**
     * Default value extracted from {@link #defaultValueAsString()} after using
     * available {@link Configuration.Converter}s.
     *
     * It makes sense to calculate it when creating {@link Configuration.Property} objects as we can
     * fail fast if annotations define a wrong default value.
     *
     * @return {@code null} if {@link #defaultValueAsString()} returns {@code null}.
     */
    Object defaultValue() ;

    /**
     * Original representation of {@link #defaultValue()} as a {@code String}.
     *
     * @return always {@code null} if {@link #defaultValue()} returns {@code null} or if
     *     {@link #maybeNull()} returns {@code true}.
     */
    String defaultValueAsString() ;

    /**
     * A pattern indicating sensible part that should not appear in clear in logs.
     *
     * @see Configuration.Inspector#safeValueOf(Configuration.Property, String)
     * @return a possibly {@code null} value.
     */
    Obfuscator obfuscator() ;

    Converter converter() ;

    /**
     * Allows nullity.
     * By default there must be a value corresponding to every {@link Property}
     * in evaluated {@link Source}s.
     * It also makes sense if there is a default value, when property evaluation
     * (like with a {@link Converter}) returns {@code null}.
     */
    boolean maybeNull() ;

    String documentation() ;


    Comparator< Property > COMPARATOR = ConfigurationProperty.COMPARATOR ;

    enum Origin {
      BUILTIN, TWEAK, EXPLICIT
    }
  }

  /**
   * Transforms a plain {@code String} into a property value.
   */
  interface Converter< T > {
    /**
     * @param input a possibly null {@code String}
     * @return the converted value, that can also be {@code null}.
     * @throws Exception if any problem occured.
     */
    T convert( String input ) throws Exception ;
  }


  /**
   * Transforms a Java method name from an interface defining a {@link Configuration}
   * into another kind of name that could use forbidden characters, like full stop (aka dot)
   * or hyphen (aka dash).
   */
  interface NameTransformer {

    String transform( String javaMethodName ) ;

  }

  /**
   * Obfuscates a value for {@link Inspector#safeValueOf(Property, String)}.
   */
  interface Obfuscator {

    /**
     * @param propertyAsString a non-null {@code String}.
     * @param replacement
     */
    String obfuscate( String propertyAsString, String replacement ) ;

  }

  /**
   * Exposes {@link Configuration} metadata.
   */
  interface Inspector< C extends Configuration > {

    ImmutableMap< String, Property< C > > properties() ;

    Property.Origin origin( Property<C> property ) ;

    Source sourceOf( Property< C > property ) ;

    ImmutableSet< Source > sources() ;

    String stringValueOf( Property< C > property ) ;

    /**
     * Returns a possibly obfuscated value if property was annotated with an
     * {@link Configuration.Property#obfuscator()}.
     */
    String safeValueOf( Property< C > property, String replacement ) ;

    /**
     * Returns the {@link Configuration.Property} corresponding
     * to every property-related method call on a {@link Configuration} object that occured
     * inside the thread in which this {@link Inspector} was created.
     * This makes this object mutable but in a way that does not affect its fundamental behavior.
     * Don't use this method outside of the thread used for creating this {@link Inspector} object.
     *
     * @return a non-null, possibly empty {@code List}.
     */
    ImmutableList< Property< C > > lastAccessed() ;

    default Property< C > last() {
      return lastAccessed().get( 0 ) ;
    }

    /**
     * Clears last accessed Properties.
     * Don't use this method outside of the thread used for creating this {@link Inspector} object.
     */
    void clearLastAccessed() ;

    Factory< C > factory() ;

  }

  /**
   * Builder-like object to use after a call to
   * {@link TemplateBasedFactory#property(Object)}.
   */
  final class PropertySetup< C extends Configuration, T > {

    private final Method lastAccessed ;
    private final SetupAcceptor setupAcceptor ;


    public PropertySetup(
        final Method lastAccessed,
        final SetupAcceptor setupAcceptor
    ) {
      this.lastAccessed = lastAccessed ;
      this.setupAcceptor = setupAcceptor ;
    }

    public PropertySetup< C, T > defaultValue( T value ) {
      setupAcceptor.accept(
          lastAccessed,
          Feature.DEFAULT_VALUE,
          value == null ? ValuedProperty.NULL_VALUE : value
      ) ;
      return this ;
    }

    /**
     * Don't call this after calling
     * {@link #nameTransformer(Configuration.NameTransformer)}.
     */
    public PropertySetup< C, T > name( final String name ) {
      setupAcceptor.accept( lastAccessed, Feature.NAME, name ) ;
      return this ;
    }

    /**
     * Don't call this after calling {@link #name(String)}.
     */
    public PropertySetup< C, T > nameTransformer( final NameTransformer nameTransformer ) {
      setupAcceptor.accept( lastAccessed, Feature.NAME_TRANSFORMER, nameTransformer ) ;
      return this ;
    }

    public PropertySetup< C, T > maybeNull() {
      setupAcceptor.accept( lastAccessed, Feature.MAYBE_NULL, true ) ;
      return this ;
    }

    public PropertySetup< C, T > converter( final Converter< T > converter ) {
      setupAcceptor.accept( lastAccessed, Feature.CONVERTER, converter ) ;
      return this ;
    }

    public PropertySetup< C, T > obfuscator( final Obfuscator obfuscator ) {
      setupAcceptor.accept( lastAccessed, Feature.OBFUSCATOR, obfuscator ) ;
      return this ;
    }

    public PropertySetup< C, T > documentation( final String text ) {
      setupAcceptor.accept( lastAccessed, Feature.DOCUMENTATION, text ) ;
      return this ;
    }

    /**
     * Call only after a call to {@link #defaultValue(Object)}.
     */
    public PropertySetup< C, T > stringValueForDefault( final String text ) {
      setupAcceptor.accept( lastAccessed, Feature.DEFAULT_VALUE_AS_STRING, text ) ;
      return this ;
    }

    enum Feature {
      DEFAULT_VALUE,
      NAME,
      NAME_TRANSFORMER,
      MAYBE_NULL,
      CONVERTER,
      OBFUSCATOR,
      DOCUMENTATION,
      DEFAULT_VALUE_AS_STRING
    }

    interface SetupAcceptor {
      void accept( Method method, Feature feature, Object object ) ;
    }

  }
}
