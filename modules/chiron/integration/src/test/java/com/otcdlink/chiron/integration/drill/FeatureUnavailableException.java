package com.otcdlink.chiron.integration.drill;

import java.util.Objects;

public class FeatureUnavailableException extends RuntimeException {

  static < FEATURE > FEATURE checkFeatureAvailable( FEATURE feature, final String message ) {
    if( feature == null ) {
      throw new FeatureUnavailableException( message ) ;
    }
    return feature ;
  }

  static < FEATURE > void checkFeatureAvailable(
      FEATURE expected,
      FEATURE actual
  ) {
    if( ! Objects.equals( expected, actual ) ) {
      throw new FeatureUnavailableException( "Expecting feature " + expected +
          " but it is " + actual ) ;
    }
  }

  static void checkFeatureAvailable( boolean available, final String message ) {
    if( ! available ) {
      throw new FeatureUnavailableException( message ) ;
    }
  }

  public FeatureUnavailableException( final String message ) {
    super( message ) ;
  }
}
