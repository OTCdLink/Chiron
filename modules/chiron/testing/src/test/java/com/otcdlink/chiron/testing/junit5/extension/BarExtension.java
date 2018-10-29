package com.otcdlink.chiron.testing.junit5.extension;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedElement;

class BarExtension implements BeforeTestExecutionCallback  {

  private static final Logger LOGGER = LoggerFactory.getLogger( BarExtension.class ) ;

  private final String message ;

  public BarExtension() {
    this( null ) ;
  }

  public BarExtension( final String message ) {
    this.message = message ;
  }

  @Override
  public void beforeTestExecution( final ExtensionContext extensionContext ) {
    final AnnotatedElement annotatedElement = extensionContext.getElement().orElseGet( null ) ;
    final String actualMessage ;
    if( message == null && annotatedElement != null ) {
      final Bar annotation = annotatedElement.getAnnotation( Bar.class ) ;
      actualMessage = annotation.message() ;
    } else {
      actualMessage = message ;
    }

    LOGGER.info( "Running " + this + "#beforeTestExecution( " + actualMessage + " ) ..." ) ;
  }
}
