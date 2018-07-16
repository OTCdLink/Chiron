package com.otcdlink.chiron.junit5;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

class FooExtension implements BeforeTestExecutionCallback  {

  private String displayNameFromContext ;

  public String displayNameFromContext() {
    return displayNameFromContext ;
  }

  @Override
  public void beforeTestExecution( final ExtensionContext context ) throws Exception {
    displayNameFromContext = "**" + context.getDisplayName() + "**" ;
  }
}
