package io.github.otcdlink.chiron.toolbox.security;

import io.github.otcdlink.chiron.toolbox.SafeSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChironSecuritySystemProperties {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      ChironSecuritySystemProperties.class ) ;
  private ChironSecuritySystemProperties() { }

  public static final SafeSystemProperty.Unvalued WEAKENED_TLS = SafeSystemProperty.forUnvalued(
      "io.github.otcdlink.chiron.toolbox.security.weakened-tls" ) ;

  static {
    if( WEAKENED_TLS.isSet() ) {
      LOGGER.warn( "Activated '" + WEAKENED_TLS.key + "', not suitable for production use." ) ;
    }
  }
}
