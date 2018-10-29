package com.otcdlink.chiron.toolbox.converter;

import com.google.common.base.Converter;

/**
 * Use this class to wrap an exception occuring in a {@link Converter#doForward(Object)}
 * into an unchecked exception, telling it can be unwrapped with no loss of information.
 */
public final class ConverterException extends RuntimeException {
  public ConverterException( final Throwable cause ) {
    super( cause ) ;
  }
}
