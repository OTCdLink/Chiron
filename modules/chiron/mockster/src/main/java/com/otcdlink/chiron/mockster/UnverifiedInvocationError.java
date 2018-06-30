package com.otcdlink.chiron.mockster;

import junit.framework.AssertionFailedError;

public class UnverifiedInvocationError extends AssertionFailedError {

  public UnverifiedInvocationError( final String detailMessage ) {
    super( detailMessage ) ;
  }
}
