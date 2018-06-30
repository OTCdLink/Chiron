package com.otcdlink.chiron.integration.drill;

public class FeatureConflictException extends RuntimeException {

  static void checkNo( final boolean condition, final String message ) {
    if( condition ) {
      throw new FeatureConflictException( message ) ;
    }
  }

  public FeatureConflictException( final String message ) {
    super( message ) ;
  }
}
