package com.otcdlink.chiron.toolbox;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.internet.Hostname;
import com.otcdlink.chiron.toolbox.text.TextTools;
import io.netty.util.ResourceLeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Adds various checks when accessing to System Properties.
 */
public abstract class SafeSystemProperty< PROPERTY, VALUE >
    implements DynamicProperty< PROPERTY, VALUE >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( SafeSystemProperty.class ) ;

  /**
   * The property key as used by {@code System.getProperty( key )}.
   */
  public final String key ;

  /**
   * The value when an instance of {@link SafeSystemProperty} was created.
   * May have evolved since, use {@link #reload()} to get a fresh {@link SafeSystemProperty}.
   */
  public final VALUE value ;

  protected final boolean requiresValue ;

  /**
   * If {@code true} and there is no such property, a warning gets logged.
   */
  protected final boolean expected ;

  protected final boolean defined ;
  protected final boolean wellFormed ;

  protected SafeSystemProperty( final String key, final boolean requiresValue, boolean expected ) {
    this.expected = expected ;
    checkArgument( ! Strings.isNullOrEmpty( key ) ) ;
    this.key = key ;
    this.requiresValue = requiresValue;
    final String rawValue = System.getProperty( key ) ;
    final String malformationMessage ;

    if( requiresValue ) {
      this.defined = ! Strings.isNullOrEmpty( rawValue ) ;
      String malformationMessageMaybe = null ;
      if( this.defined ) {
        VALUE decoded = null ;
        try {
          decoded = decode( rawValue ) ;
        } catch( final Exception e ) {
          malformationMessageMaybe = "can't parse '" + rawValue + "'" ;
        }
        value = decoded ;
      } else {
        malformationMessageMaybe = "should have a value" ;
        this.value = null ;
      }
      malformationMessage = malformationMessageMaybe ;
    } else {
      this.value = null ;
      if( rawValue == null ) {
        defined = false ;
        malformationMessage = null ;
      } else if( rawValue.isEmpty() ) {
        defined = true ;
        malformationMessage = null ;
      } else {
        defined = true ;
        malformationMessage = "expected no value, found '" + rawValue + "' instead" ;
      }
    }

    this.wellFormed = malformationMessage == null ;

    if( wellFormed ) {
      LOGGER.trace( "Successfully parsed " + this.toString( 100, true ) + "." ) ;
    } else {
      if( expected ) {
        LOGGER.warn( "Malformed property '" + key + "': " + malformationMessage + "." ) ;
      }
    }

  }

  @Override
  public final VALUE valueOrDefault( final VALUE defaultValue ) {
    return defined && wellFormed ? value : defaultValue ;
  }

  /**
   * @param value the value to decode.
   */
  protected abstract VALUE decode( String value ) throws Exception ;

  /**
   * Call {@code System.setProperty( key, value )} back again, and reload.
   */
  public abstract PROPERTY save() ;

  public final PROPERTY clear() {
    System.clearProperty( key ) ;
    return reload() ;
  }

  /**
   * Default implementation uses reflexion.
   * Subclasses with custom fields should override this method.
   */
  @Override
  public PROPERTY reload() {
    final Constructor< ? >[] constructors = getClass().getDeclaredConstructors() ;
    Throwable problem = null ;
    try {
      for( final Constructor constructor : constructors ) {
        final Class[] parameterTypes = constructor.getParameterTypes() ;
        if( parameterTypes.length == 2 &&
            String.class.equals( parameterTypes[ 0 ] ) &&
            Boolean.TYPE.equals( parameterTypes[ 1 ] )
        ) {
            return ( PROPERTY ) constructor.newInstance( key, expected ) ;
        } else if( parameterTypes.length == 3 &&
            String.class.equals( parameterTypes[ 0 ] ) &&
            Boolean.TYPE.equals( parameterTypes[ 1 ] ) &&
            Boolean.TYPE.equals( parameterTypes[ 2 ] )
          ) {
          return ( PROPERTY ) constructor.newInstance( key, requiresValue, expected ) ;
        }
      }
    } catch( InstantiationException | IllegalAccessException | InvocationTargetException e ) {
      problem = e ;
    }
    final String message ;
    if( problem == null ) {
      message = "valid contructor missing" ;
    } else {
      message = "encountered " + problem.getClass().getSimpleName() + ": " + problem.getMessage() ;
    }
    throw new UnsupportedOperationException( "Failed to reload " + this + ", " + message ) ;
  }

  @Override
  public String toString() {
    return toString( Integer.MAX_VALUE, false ) ;
  }

  public String toString( final int maximumValueLength, final boolean sanitize ) {
    final String rawValue = Objects.toString( value ) ;
    final String printableText ;
    printableText = sanitize ? TextTools.toPrintableAscii( rawValue ) : rawValue ;
    return
        ToStringTools.getNiceClassName( this ) + "{" +
        key + "=>" +
        TextTools.trimToLength( printableText, maximumValueLength ) +
        "}"
    ;
  }

  public abstract static class Valued< PROPERTY, VALUE >
      extends SafeSystemProperty< PROPERTY, VALUE >
  {
    protected Valued( final String key, final boolean expected ) {
      super( key, true, expected ) ;
    }

    public final PROPERTY set( final VALUE value ) {
      System.setProperty( key, encode( value ) ) ;
      return reload() ;
    }

    protected abstract String encode( VALUE value ) ;

    public final PROPERTY save() {
      if( value == null ) {
        clear() ;
      } else {
        checkState( wellFormed, "Can't save because an ill-formed value for " + this ) ;
        System.setProperty( key, encode( value ) ) ;
      }
      return reload() ;
    }

  }

  public static final class Unvalued extends SafeSystemProperty< Unvalued, Unvalued.NoValue > {
    protected Unvalued( final String key, final boolean expected) {
      super( key, false, expected ) ;
    }

    public boolean isSet() {
      return defined && wellFormed ;
    }

    @Override
    protected NoValue decode( final String value ) throws Exception {
      throw new Exception( "Should have no value" ) ;
    }

    public Unvalued save() {
      if( isSet() ) {
        System.setProperty( key, "" ) ;
      } else {
        clear() ;
      }
      return reload() ;
    }

    public static final class NoValue { }
  }




  public static class BooleanType extends Valued< BooleanType, Boolean > {
    protected BooleanType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }


    @Override
    protected Boolean decode( final String value ) {
      return Boolean.parseBoolean( value ) ;
    }

    @Override
    protected String encode( final Boolean aBoolean ) {
      return Boolean.toString( aBoolean ) ;
    }

    public final boolean explicitelyTrue() {
      return defined && value;
    }

    /**
     * Same as {@link #valueOrDefault(Object)} with {@code boolean} coercion.
     */
    public boolean trueOrDefault( final boolean defaultValue ) {
      return valueOrDefault( defaultValue ) ;
    }
  }

  public static class StringType extends Valued< StringType, String > {
    protected StringType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }

    @Override
    protected String decode( final String rawString ) {
      return rawString ;
    }

    @Override
    protected String encode( String value ) {
      return value ;
    }
  }

  public static class StringListType extends Valued< StringListType, ImmutableList< String > > {
    @SuppressWarnings( "unused" )
    private final String separator ;
    protected StringListType( final String key, final String separator, final boolean expected ) {
      super( capture( key, separator ), expected ) ;
      checkArgument( ! TextTools.isBlank( separator ) ) ;
      this.separator = separator ;
      SEPARATOR_CAPTURE.remove() ;
    }

    private static final ThreadLocal< String > SEPARATOR_CAPTURE = new ThreadLocal<>() ;

    private static String capture( final String key, final String separator ) {
      checkArgument( ! Strings.isNullOrEmpty( separator ) ) ;
      SEPARATOR_CAPTURE.set( separator ) ;
      return key ;
    }

    @Override
    protected ImmutableList< String > decode( final String value ) {
      final String separator = SEPARATOR_CAPTURE.get() ;
      return ImmutableList.copyOf( Splitter.on( separator ).splitToList( value ) ) ;
    }

    @Override
    protected String encode( final ImmutableList< String > strings ) {
      return Joiner.on( separator ).join( strings ) ;
    }
  }

  public static class EnumType< E extends Enum< E > >
      extends Valued< SafeSystemProperty.EnumType< E >, E >
  {
    private final Class< E > enumClass ;

    protected EnumType( final String key, final Class< E > enumClass, final boolean expected ) {
      super( captureEnumClass( key, enumClass ), expected ) ;
      this.enumClass = checkNotNull( enumClass ) ;
    }

    @Override
    public EnumType< E > reload() {
      return new EnumType<>( key, enumClass, expected ) ;
    }

    private static final ThreadLocal< Class< ? extends Enum > > ENUMCLASS_CAPTURE =
        new ThreadLocal<>() ;

    private static String captureEnumClass(
        final String s,
        final Class< ? extends Enum > enumClass
    ) {
      ENUMCLASS_CAPTURE.set( enumClass ) ;
      return s ;
    }





    @Override
    protected E decode( final String value ) {
      final Class< E > aClass = ( Class<E> ) ENUMCLASS_CAPTURE.get() ;
      return Enum.valueOf( aClass, value ) ;
    }

    @Override
    protected String encode( E enumElement ) {
      return enumElement.name() ;
    }
  }


  public static class IntegerType extends Valued< IntegerType, Integer > {

    protected IntegerType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }

    @Override
    protected Integer decode( final String value ) throws Exception {
      return Integer.parseInt( value ) ;
    }

    @Override
    protected String encode( Integer integer ) {
      return Integer.toString( integer ) ;
    }

    /**
     * Same as {@link #valueOrDefault(Object)} with {@code int} coercion.
     */
    public final int intValue( final int defaultValue ) {
      return valueOrDefault( defaultValue ) ;
    }
  }

  public static class StrictlyPositiveIntegerType extends IntegerType {

    protected StrictlyPositiveIntegerType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }

    @Override
    protected Integer decode( final String value ) throws Exception {
      final Integer any = super.decode( value ) ;
      checkArgument( any == null || any > 0, "Must be strictly positive" ) ;
      return any ;
    }

  }

  public static class FileType extends Valued< FileType, File > {

    protected FileType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }

    @Override
    protected File decode( final String value ) {
      return new File( value ) ;
    }

    @Override
    protected String encode( File file ) {
      return file.getAbsolutePath() ;
    }
  }

  public static class HostnameType extends Valued< HostnameType, Hostname> {

    protected HostnameType( final String key, final boolean expected ) {
      super( key, expected ) ;
    }

    @Override
    protected Hostname decode( final String value ) {
      return Hostname.parse( value ) ;
    }

    @Override
    protected String encode( final Hostname hostname ) {
      return hostname.asString() ;
    }
  }

  public static Unvalued forUnvalued( final String key ){
    return new Unvalued( key, false ) ;
  }

  public static Unvalued forUnvalued( final String key, final boolean expected ){
    return new Unvalued( key, expected ) ;
  }

  public static BooleanType forBoolean( final String key ){
    return new BooleanType( key, true ) ;
  }

  public static BooleanType forBoolean( final String key, final boolean expected ){
    return new BooleanType( key, expected ) ;
  }

  public static StringType forString( final String key ){
    return new StringType( key, true ) ;
  }

  public static StringType forString( final String key, final boolean expected ){
    return new StringType( key, expected ) ;
  }

  public static StringListType forStringList( final String key, final String separator ){
    return new StringListType( key, separator, true ) ;
  }

  public static StringListType forStringList(
      final String key,
      final String separator,
      final boolean expected
  ){
    return new StringListType( key, separator, expected ) ;
  }

  public static < E extends Enum< E > > EnumType< E > forEnum(
      final String key,
      final Class< E > enumClass
  ) {
    return forEnum( key, enumClass, true ) ;
  }

  public static < E extends Enum< E > > EnumType< E > forEnum(
      final String key,
      final Class< E > enumClass,
      final boolean expected
  ) {
    return new EnumType<>( key, enumClass, expected ) ;
  }

  public static IntegerType forInteger( final String key ){
    return new IntegerType( key, true ) ;
  }

  public static IntegerType forInteger( final String key, final boolean expected ){
    return new IntegerType( key, expected ) ;
  }

  public static IntegerType forStrictlyPositiveInteger( final String key ){
    return new StrictlyPositiveIntegerType( key, true ) ;
  }

  public static IntegerType forStrictlyPositiveInteger( final String key, final boolean expected ){
    return new StrictlyPositiveIntegerType( key, expected ) ;
  }

  public static FileType forFile( final String key ){
    return new FileType( key, true ) ;
  }

  public static FileType forFile( final String key, final boolean expected ){
    return new FileType( key, expected ) ;
  }

  public static HostnameType forHostname( final String key ){
    return new HostnameType( key, true ) ;
  }
  public static HostnameType forHostname( final String key, final boolean expected ){
    return new HostnameType( key, expected ) ;
  }

  /**
   * All <a href="http://docs.oracle.com/javase/8/docs/api/java/lang/System.html#getProperties--" >Standard System Properties</a>,
   * minus deprecated ones.
   * TODO: add all of them.
   */
  public interface Standard {
    FileType USER_HOME = forFile( "user.home" ) ;

    FileType USER_DIR = forFile( "user.dir" ) ;

    StringType USER_NAME = forString( "user.name" ) ;

    FileType JAVA_HOME = forFile( "java.home" ) ;

    FileType JAVA_IO_TMPDIR = forFile( "java.io.tmpdir" ) ;

    StringType LINE_SEPARATOR = forString( "line.separator" ) ;
    StringType PATH_SEPARATOR = forString( "path.separator" ) ;

    StringListType JAVA_CLASS_PATH = forStringList( "java.class.path", PATH_SEPARATOR.value ) ;

    StringType OS_NAME = forString( "os.name" ) ;

    interface OperatingSystem {
      static boolean isMacOsX() {
        return OS_NAME.defined && OS_NAME.value.startsWith( "Mac OS X" ) ;
      }
    }
  }

  /**
   * Most of time we don't need to access those properties but it's quicker to have a look here
   * than to run a Google search.
   */
  public interface Netty {

    /**
     * It turns out that this property only makes sense when setting a system property
     * when launching an external process.
     *
     * @see io.netty.util.ResourceLeakDetector#setLevel(ResourceLeakDetector.Level)
     */
    EnumType< ResourceLeakDetector.Level > LEAK_DETECTION_LEVEL =
        forEnum( "io.netty.leakDetectionLevel", ResourceLeakDetector.Level.class, false ) ;
  }

}
