package com.otcdlink.chiron.upend.intraday;

import java.io.IOException;

public class RuntimeIOException extends RuntimeException {

  public RuntimeIOException( final IOException cause ) {
    super( cause ) ;
  }
}
