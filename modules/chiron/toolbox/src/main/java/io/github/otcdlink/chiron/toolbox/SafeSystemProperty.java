package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import io.github.otcdlink.chiron.toolbox.text.TextTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Adds various checks when accessing to System Properties.
 */
public abstract class SafeSystemProperty< PROPERTY, VALUE >
    implements DynamicProperty< PROPERTY, VALUE >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( SafeSystemProperty.class ) ;

  public final String key ;

  /**
   * The value when an instance of {@link SafeSystemProperty} was created.
   * May have evolved since, use {@link #reload()} to get a fresh {@link SafeSystemProperty}.
   */
  public final VALUE value ;

  protected final boolean expectsValue ;
  protected final boolean defined ;
  protected final boolean wellFormed ;

  protected SafeSystemProperty( final String key, final boolean expectsValue ) {
    checkArgument( ! Strings.isNullOrEmpty( key ) ) ;
    this.key = key ;
    this.expectsValue = expectsValue ;
    final String rawValue = System.getProperty( key ) ;
    final String malformationMessage ;

    if( expectsValue ) {
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
      LOGGER.debug( "Successfully parsed " + this.toString( 100, true ) + "." ) ;
    } else {
      LOGGER.warn( "Malformed property '" + key + "': " + malformationMessage + "." ) ;
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

  @Override
  public PROPERTY reload() {
    final Constructor< ? >[] constructors = getClass().getDeclaredConstructors() ;
    Throwable problem = null ;
    for( final Constructor constructor : constructors ) {
      final Class[] parameterTypes = constructor.getParameterTypes() ;
      if( parameterTypes.length == 1 && String.class.equals( parameterTypes[ 0 ] ) ) {
        try {
          return ( PROPERTY ) constructor.newInstance( key ) ;
        } catch( InstantiationException | IllegalAccessException | InvocationTargetException e ) {
          problem = e ;
        }
      }
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
    public Valued( final String key ) {
      super( key, true ) ;
    }
  }

  public static final class Unvalued extends SafeSystemProperty< Unvalued, Unvalued.NoValue > {
    public Unvalued( final String key ) {
      super( key, false ) ;
    }

    public boolean isSet() {
      return defined && wellFormed ;
    }

    @Override
    protected NoValue decode( final String value ) throws Exception {
      throw new Exception( "Should have no value" ) ;
    }

    public static final class NoValue {
      public final NoValue INSTANCE = new NoValue() ;

      @Override
      public String toString() {
        return ToStringTools.getNiceClassName( this ) + "{}" ;
      }
    }
  }




  public static class BooleanType extends Valued< BooleanType, Boolean > {
    protected BooleanType( final String key ) {
      super( key ) ;
    }


    @Override
    protected Boolean decode( final String value ) {
      return Boolean.parseBoolean( value ) ;
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
    protected StringType( final String key ) {
      super( key ) ;
    }

    @Override
    protected String decode( final String value ) {
      return value ;
    }
  }

  public static class StringListType extends Valued< StringListType, ImmutableList< String > > {
    @SuppressWarnings( "unused" )
    private final String separator ;
    protected StringListType( final String key, final String separator ) {
      super( capture( key, separator ) ) ;
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
  }

  public static class IntegerType extends Valued< IntegerType, Integer > {

    protected IntegerType( final String key ) {
      super( key ) ;
    }

    @Override
    protected Integer decode( final String value ) throws Exception {
      return Integer.parseInt( value ) ;
    }

    /**
     * Same as {@link #valueOrDefault(Object)} with {@code int} coercion.
     */
    public final int intValue( final int defaultValue ) {
      return valueOrDefault( defaultValue ) ;
    }
  }

  public static class StrictlyPositiveIntegerType extends IntegerType {

    protected StrictlyPositiveIntegerType( final String key ) {
      super( key ) ;
    }

    @Override
    protected Integer decode( final String value ) throws Exception {
      final Integer any = super.decode( value ) ;
      checkArgument( any == null || any > 0, "Must be strictly positive" ) ;
      return any ;
    }

  }

  public static class FileType extends Valued< FileType, File > {

    protected FileType( final String key ) {
      super( key ) ;
    }

    @Override
    protected File decode( final String value ) {
      return new File( value ) ;
    }

  }

  public static class HostnameType extends Valued< HostnameType, Hostname > {

    protected HostnameType( final String key ) {
      super( key ) ;
    }

    @Override
    protected Hostname decode( final String value ) {
      return Hostname.parse( value ) ;
    }

  }

  public static Unvalued forUnvalued( final String key ){
    return new Unvalued( key ) ;
  }

  public static BooleanType forBoolean( final String key ){
    return new BooleanType( key ) ;
  }

  public static StringType forString( final String key ){
    return new StringType( key ) ;
  }

  public static StringListType forStringList( final String key, final String separator ){
    return new StringListType( key, separator ) ;
  }

  public static IntegerType forInteger( final String key ){
    return new IntegerType( key ) ;
  }

  public static IntegerType forStrictlyPositiveInteger( final String key ){
    return new StrictlyPositiveIntegerType( key ) ;
  }

  public static FileType forFile( final String key ){
    return new FileType( key ) ;
  }

  public static HostnameType forHostname( final String key ){
    return new HostnameType( key ) ;
  }

  /**
   * All <a href="http://docs.oracle.com/javase/8/docs/api/java/lang/System.html#getProperties--" >Standard System Properties</a>,
   * minus deprecated ones.
   * TODO: add all of them.
   */
  public interface Standard {
    FileType USER_HOME = forFile( "user.home" ) ;

    FileType USER_DIR = forFile( "user.dir" ) ;

    FileType JAVA_HOME = forFile( "java.home" ) ;

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

}
