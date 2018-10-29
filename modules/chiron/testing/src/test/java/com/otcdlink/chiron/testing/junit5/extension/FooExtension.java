package com.otcdlink.chiron.testing.junit5.extension;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FooExtension implements BeforeTestExecutionCallback  {

  private static final Logger LOGGER = LoggerFactory.getLogger( FooExtension.class ) ;

  private String displayNameFromContext ;

  String displayNameFromContext() {
    return displayNameFromContext ;
  }

  @Override
  public void beforeTestExecution( final ExtensionContext context ) throws Exception {
    LOGGER.info( "Running " + this + "#beforeTestExecution ..." ) ;
    displayNameFromContext = "**" + context.getDisplayName() + "**" ;
    final ReusableStuff reusableStuff = ReusableStuff.loadInTestScope( context ) ;
    LOGGER.info( "(Re)using " + reusableStuff + "." ) ;
  }

}
