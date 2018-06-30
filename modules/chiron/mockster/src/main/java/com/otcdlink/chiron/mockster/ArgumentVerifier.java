package com.otcdlink.chiron.mockster;

public interface ArgumentVerifier< OBJECT > {

  /**
   *
   * @param location a non-null object.
   * @param actualValue a possibly null object.
   * @throws AssertionError if verification failed.
   *
   */
  void verify( ArgumentEvaluator.Location location, OBJECT actualValue ) ;
}
