package io.github.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;

/**
 * {@link Converter}s.
 */
public final class InternetStringConverters {

  public static Converter< Hostname, String > HOSTNAME = new Converter< Hostname, String >() {
    @Override
    protected Hostname doBackward( final @Nonnull String string ) {
      return Hostname.parse( string ) ;
    }

    @Override
    protected String doForward( final @Nonnull Hostname hostname ) {
      return hostname.asString() ;
    }
  } ;

  private InternetStringConverters() { }

  public static final Converter< HostPort, String > HOSTPORT =
      new Converter< HostPort, String >() {
        @Override
        protected HostPort doBackward( final @Nonnull String string ) {
          try {
            return HostPort.parse( string ) ;
          } catch( HostPort.ParseException e ) {
            throw new RuntimeException( e ) ;
          }
        }

        @Override
        protected String doForward( final @Nonnull HostPort hostPort ) {
          return hostPort.asString() ;
        }
      }
  ;

  public static final Converter< String, EmailAddress > EMAIL_ADDRESS =
      new Converter<String, EmailAddress>() {
        @Override
        protected EmailAddress doForward( final @Nonnull String string ) {
          try {
            return new EmailAddress( string ) ;
          } catch( EmailAddressFormatException e ) {
            throw new RuntimeException( e ) ;
          }
        }

        @Override
        protected String doBackward( final @Nonnull EmailAddress emailAddress ) {
          return emailAddress.asString() ;
        }
      }
  ;

  public static final Converter< ImmutableSet< EmailAddress >, String > EMAIL_ADDRESSES =
      new Converter< ImmutableSet< EmailAddress >, String >() {
        @Override
        protected ImmutableSet< EmailAddress > doBackward( @Nonnull final String string ) {
          try {
            return EmailAddress.parseMultipleAddresses( string ) ;
          } catch( EmailAddressFormatException e ) {
            throw new RuntimeException( e ) ;
          }
        }

        @Override
        protected String doForward( final @Nonnull ImmutableSet< EmailAddress > emailAddresses ) {
          return EmailAddress.join( emailAddresses ) ;
        }
      }
  ;


}
