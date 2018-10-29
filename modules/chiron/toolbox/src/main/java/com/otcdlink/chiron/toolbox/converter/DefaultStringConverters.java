package com.otcdlink.chiron.toolbox.converter;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.otcdlink.chiron.toolbox.SafeSystemProperty;
import com.otcdlink.chiron.toolbox.internet.EmailAddress;
import com.otcdlink.chiron.toolbox.internet.HostAccessFormatException;
import com.otcdlink.chiron.toolbox.internet.Hostname;
import com.otcdlink.chiron.toolbox.internet.SmtpHostAccess;
import com.otcdlink.chiron.toolbox.number.Fraction;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;

/**
 * General purpose {@link Converter}s, all of {@code < String, T >} type, using sensible defaults.
 * <p>
 * Sensible defaults mean:
 * <ul>
 *   <li>
 *     Default parsing method of the type to forward-convert to.
 *   </li><li>
 *     Human-readable under the {@code String} format.
 *   </li><li>
 *     Password obfuscation if there is one password.
 *   </li><li>
 *     Symmetric conversion whenever possible. (Not possible with password obfuscation.)
 *   </li><li>
 *     Wrap checked exceptions in a {@link ConverterException}.
 *   </li>
 * </ul>
 */
public final class DefaultStringConverters {

  private DefaultStringConverters() { }

  /**
   * Converts some kind of parser to a {@link Converter} using {@link Object#toString()}
   * for backward conversion.
   *
   * @param function a function that doesn't have to support null input.
   */
  public static< T > Converter< String, T > from( final Function< String, T > function ) {
    return Converter.from( function::apply, Object::toString ) ;
  }


  public static final Converter< String, Integer > INTEGER = Ints.stringConverter() ;

  public static final Converter< String, Boolean > BOOLEAN = Converter.from(
      Boolean::parseBoolean, Object::toString ) ;

  public static final Converter< String, File > FILE = new Converter< String, File >() {
    @Override
    protected File doForward( @Nonnull final String input ) {
      if( input.startsWith( "~" + File.separator ) ) {
        return new File( SafeSystemProperty.Standard.USER_HOME.value, input.substring( 2 ) ) ;
      }
      return new File( input ) ;
    }

    @Override
    protected String doBackward( @Nonnull final File file ) {
      return file.getPath() ;
    }

  } ;

  public static final Converter< String, URL > URL = new Converter< String, URL >() {

    @Override
    protected URL doForward( @Nonnull final String string ) {
      try {
        return new URL( string ) ;
      } catch( final MalformedURLException e ) {
        throw new ConverterException( e ) ;
      }
    }

    @Override
    protected String doBackward( @Nonnull final URL url ) {
      return url.toString() ;
    }

  } ;

  public static final Converter< String, Fraction > FRACTION =
      new Converter< String, Fraction >() {
        @Override
        protected Fraction doForward( @Nonnull final String string ) {
          return new Fraction( Float.parseFloat( string ) ) ;
        }

        @Override
        protected String doBackward( @Nonnull final Fraction fraction ) {
          return fraction.asString() ;
        }
      }
  ;

  public static final Converter< String, LocalDate > LOCAL_DATE =
      new Converter< String, LocalDate >() {
        @Override
        protected LocalDate doForward( final @Nonnull String string ) {
          return LOCALDATE_FORMATTER.parseLocalDate( string ) ;
        }

        @Override
        protected String doBackward( final @Nonnull LocalDate localDate ) {
          return LOCALDATE_FORMATTER.print( localDate ) ;
        }
      }
  ;

  public static final DateTimeFormatter LOCALDATE_FORMATTER =
      DateTimeFormat.forPattern( "yyyy-MM-dd" ) ;

  public static final Converter< String, Duration > DURATION = new Converter< String, Duration >() {
    @Override
    public Duration doForward( @Nonnull final String input ) {
      return Duration.millis( Integer.parseInt( input ) ) ;
    }

    @Override
    protected String doBackward( Duration duration ) {
      return Long.toString( duration.getMillis() ) ;
    }
  } ;


  public static final Converter< String, Hostname > HOSTNAME = new Converter< String, Hostname >() {
    @Override
    public Hostname doForward( @Nonnull final String input ) {
      return Hostname.parse( input ) ;
    }

    @Override
    protected String doBackward( @Nonnull final Hostname hostname ) {
      return hostname.asString() ;
    }
  } ;


  public static Converter< String, EmailAddress > EMAIL_ADDRESS =
      new Converter< String, EmailAddress >() {

        @Override
        protected String doBackward( @Nonnull final EmailAddress emailAddress ) {
          return emailAddress.asString() ;
        }

        @Override
        protected EmailAddress doForward( @Nonnull final String string ) {
          return EmailAddress.parseQuiet( string ) ;
        }
      }
  ;

  public static final Converter< String, SmtpHostAccess > SMTPHOSTACCESS =
      new Converter< String, SmtpHostAccess >() {
        @Override
        public SmtpHostAccess doForward( @Nonnull final String input ) {
          try {
            return SmtpHostAccess.parse( input ) ;
          } catch( HostAccessFormatException e ) {
            throw new ConverterException( e ) ;
          }
        }

        @Override
        protected String doBackward( @Nonnull final SmtpHostAccess smtpHostAccess ) {
          return smtpHostAccess.asString() ;
        }
      }
  ;


  public static final ImmutableMap< Class< ? >, Converter > ALL
      = ImmutableMap.< Class< ? >, Converter >builder()
          .put( String.class, Converter.< String >identity() )
          .put( Integer.TYPE, INTEGER )
          .put( Integer.class, INTEGER )
          .put( Boolean.TYPE, BOOLEAN )
          .put( Boolean.class, BOOLEAN )
          .put( File.class, FILE )
          .put( URL.class, URL )
          .put( Duration.class, DURATION )
          .put( LocalDate.class, LOCAL_DATE )
          .put( Hostname.class, HOSTNAME )
          .put( EmailAddress.class, EMAIL_ADDRESS )
          .put( SmtpHostAccess.class, SMTPHOSTACCESS )
          .build()
  ;

}
