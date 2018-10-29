package com.otcdlink.chiron.testing.junit5.parameter;

import org.junit.jupiter.api.TestInfo;

public class SomeHelper {

  private int i ;
  private String s ;

  /**
   * The {@literal @ParametersReceiver} annotation tells to set the parameters defined
   * in the test.
   */
  // @ParametersReceiver
  public void parameterize( final int i, final String s, final TestInfo testInfo ) {
    this.i = i ;
    this.s = s ;
  }

  public String parameters() {
    return "{ " + i + ", " + s + " }" ;
  }
}
