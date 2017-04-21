package io.github.otcdlink.chiron.configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.net.URL;
import java.util.function.Function;

/**
 * Default {@link Configuration.Converter}s.
 */
public final class Converters {

  private Converters() { }

  /**
   * @param function a function that doesn't have to support null input.
   */
  public static< T > Configuration.Converter< T > from( final Function< String, T > function ) {
    Preconditions.checkNotNull( function ) ;
    return new AbstractConverter< T >() {
      @Override
      protected T convertFromNonNull( final String input ) throws Exception {
        return function.apply( input ) ;
      }
      @Override
      public String toString() {
        return Converters.class.getSimpleName() + "$from{" + function + "}" ;
      }
    } ;
  }


  public static abstract class AbstractConverter< T > implements Configuration.Converter< T > {

    @Override
    public T convert( final String input ) throws Exception {
      if( input == null ) {
        return null ;
      } else {
        return convertFromNonNull( input ) ;
      }
    }

    protected T convertFromNonNull( final String input ) throws Exception {
      return null ;
    }

    @Override
    public String toString() {
      return ConfigurationTools.getNiceName( getClass() ) ;
    }
  }

  public static final Configuration.Converter< String > INTO_STRING = new AbstractConverter< String >() {
    @Override
    public String convert( String input ) {
      return input ;
    }
  } ;

  public static final Configuration.Converter< Integer > INTO_INTEGER_PRIMITIVE
      = new AbstractConverter< Integer >()
  {
    @Override
    public Integer convert( final String input ) {
        return Integer.parseInt( input ) ;
    }
  } ;

  public static final Configuration.Converter< Integer > INTO_INTEGER_OBJECT
      = new AbstractConverter< Integer >()
  {
    @Override
    public Integer convert( final String input ) throws Exception {
      if( Strings.isNullOrEmpty( input ) ) {
        return null ;
      }
      return Integer.parseInt( input ) ;
    }
  } ;

  public static final Configuration.Converter< Boolean > INTO_BOOLEAN_PRIMITIVE
      = new AbstractConverter< Boolean >()
  {
    @Override
    public Boolean convert( final String input ) {
        return Boolean.parseBoolean( input ) ;
    }
  } ;

  public static final Configuration.Converter< Boolean > INTO_BOOLEAN_OBJECT
      = new AbstractConverter< Boolean >()
  {
    @Override
    public Boolean convert( final String input ) throws Exception {
      if( Strings.isNullOrEmpty( input ) ) {
        return null ;
      }
      return Boolean.parseBoolean( input ) ;
    }
  } ;

  private static final File USER_HOME = new File( System.getProperty( "user.home" ) ) ;

  public static final Configuration.Converter< File > INTO_FILE = new AbstractConverter< File >() {
    @Override
    public File convert( final String input ) throws Exception {
      if( Strings.isNullOrEmpty( input ) ) {
        return null ;
      }
      if( input.startsWith( "~" + File.separator ) ) {
        return new File( USER_HOME, input.substring( 2 ) ) ;
      }
      return new File( input ) ;
    }
  } ;

  public static final Configuration.Converter< URL > INTO_URL = new AbstractConverter< URL >() {
    @Override
    public URL convert( final String input ) throws Exception {
      if( Strings.isNullOrEmpty( input ) ) {
        return null ;
      }
      return new URL( input ) ;
    }
  } ;

  public static final ImmutableMap< Class< ? >, Configuration.Converter> DEFAULTS
      = ImmutableMap.< Class< ? >, Configuration.Converter>builder()
          .put( String.class, ( Configuration.Converter ) INTO_STRING )
          .put( Integer.TYPE, INTO_INTEGER_PRIMITIVE )
          .put( Integer.class, INTO_INTEGER_OBJECT )
          .put( Boolean.TYPE, INTO_BOOLEAN_PRIMITIVE )
          .put( Boolean.class, INTO_BOOLEAN_OBJECT )
          .put( File.class, INTO_FILE )
          .put( URL.class, INTO_URL )
          .build()
  ;

}
