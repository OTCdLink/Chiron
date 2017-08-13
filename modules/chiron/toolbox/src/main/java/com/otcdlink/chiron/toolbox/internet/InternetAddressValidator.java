package com.otcdlink.chiron.toolbox.internet;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InternetAddressValidator {

  private static final String IP_ADDRESS =
      "(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])"
    + "\\.(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])"
    + "\\.(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])"
    + "\\.(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])"
  ;

  public static final String HOST_NAME = "[a-zA-Z][-a-zA-Z0-9]*(?:\\.[a-zA-Z][-a-zA-Z0-9]*)*" ;

  private static final Pattern HOST_ADDRESS = Pattern.compile(
        "(?:"
          + IP_ADDRESS
      + ")|(?:"
          + HOST_NAME
      + ")"
  ) ;

  public static boolean isHostnameValid( final String hostname ) {
    return HOST_ADDRESS.matcher( hostname ).matches() || isDomainNameValid( hostname ) ;
  }

  private static final String RAW_DOMAIN_NAME =
      "(?:[a-z0-9](?:[-a-z0-9]*[a-z0-9])?\\.)+(?:[a-z0-9]{2,})" ;

  /**
   * See also:
   * http://www.shauninman.com/archive/2006/05/08/validating_domain_names
   */
  private static final Pattern DOMAIN_NAME = Pattern.compile(
      "^" + RAW_DOMAIN_NAME + "$" ) ;

  public static boolean isDomainNameValid( final String domainName ) {
    return DOMAIN_NAME.matcher( domainName ).matches() ;
  }

  private static final Pattern EMAIL_ADDRESS = Pattern.compile(
      "[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])" + "@" + RAW_DOMAIN_NAME
  ) ;

  public static boolean isEmailAddressValid( final String emailAddress ) {
    return EMAIL_ADDRESS.matcher( emailAddress ).matches() ;
  }

  private static final Pattern NAMED_HOST = Pattern.compile(
      "[a-z0-9](?:[-a-z0-9]*[a-z0-9])+(?:\\.[a-z0-9](?:[-a-z0-9]*[a-z0-9]))*" ) ;

  private static final Pattern HOST = Pattern.compile(
        "(?:"
            + NAMED_HOST
      + ")|(?:"
            + IP_ADDRESS
      + ")"
  ) ;

  private static final Pattern PORT = Pattern.compile( "[0-9]+" ) ;

  private static final Pattern HOST_PORT = Pattern.compile(
      "(" + HOST.pattern() + "):(" + PORT.pattern() + ")" ) ;

  private static final Pattern HOST_WITH_OPTIONAL_PORT = Pattern.compile(
      "(" + HOST.pattern() + ")(?::(" + PORT.pattern() + "))?" ) ;

  private static final Pattern HTTP_HOST = Pattern.compile(
      "(?:(https?)://)?" + "(" + HOST.pattern() + "):(" + PORT.pattern() + ")" ) ;


  private static final Pattern NAME = Pattern.compile( "[\\w]+(?:(?:[-\\.\\w])[\\w]+)*" ) ;

  /**
   * Don't allow characters that may conflict with command-line arguments or regex syntax.
   */
  public static final Pattern PASSWORD = Pattern.compile(
      "[-_$=+/?§\\w]+" ) ;

  private static final Pattern SMTPHOST_ACCESS_PATTERN = Pattern.compile(
        "(" + NAME.pattern() + "(?:@" + NAMED_HOST.pattern() + ")?" + ")"
            + ":(" + PASSWORD.pattern() + ")"
      + "@"
      + "(" + HOST.pattern() + ")"
      + ":(" + PORT.pattern() + ")"
  ) ;

  private static final Pattern PARTIAL_HOST_ACCESS_PATTERN = Pattern.compile(
        "(" + NAME.pattern() + ")"
      + "@"
      + "(" + HOST.pattern() + ")"
      + "(?::(" + PORT.pattern() + "))?"
  ) ;

  private static final Pattern COMETDHOST_ACCESS_PATTERN = Pattern.compile(
        "(https?)://"
      + "(?:"
          + "(" + NAME.pattern() + ")"
          + "(?::(" + PASSWORD.pattern() + ")"
      + ")?@)?"
      + "(" + HOST.pattern() + ")"
      + "(?::(" + PORT.pattern() + "))?"
  ) ;

  public static Matcher smtpHostAccessMatcher( final String hostAccess ) {
    return SMTPHOST_ACCESS_PATTERN.matcher( hostAccess ) ;
  }

  public static Matcher partialHostAccessMatcher( final String hostAccess ) {
    return PARTIAL_HOST_ACCESS_PATTERN.matcher( hostAccess ) ;
  }

  public static Matcher cometdHostAccessMatcher( final String hostAccess ) {
    return COMETDHOST_ACCESS_PATTERN.matcher( hostAccess ) ;
  }

  public static Matcher hostPortMatcher( final String hostAccess ) {
    return HOST_PORT.matcher( hostAccess ) ;
  }

  public static Matcher hostWithOptionalPortMatcher( final String hostAccess ) {
    return HOST_WITH_OPTIONAL_PORT.matcher( hostAccess ) ;
  }

  public static Matcher httpHostMatcher( final String hostAccess ) {
    return HTTP_HOST.matcher( hostAccess ) ;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger( InternetAddressValidator.class ) ;
  static {
    for( final Field field : InternetAddressValidator.class.getDeclaredFields() ) {
      if( Modifier.isStatic( field.getModifiers() ) && Pattern.class.equals( field.getType() ) ) {
        try {
          // What's logged here may appear in launcher's stdout since loading happens very early.
          LOGGER.trace( "Crafted " + field.getName() + " regex: " + field.get( null ) ) ;
        } catch( final IllegalAccessException e ) {
          Throwables.propagate( e ) ;
        }
      }
    }
  }

}
