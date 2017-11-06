package com.otcdlink.chiron.toolbox.diagnostic;

import org.junit.Test;

import java.io.StringWriter;

public class SystemPropertiesDiagnosticTest {

  @Test
  public void passwordObfuscationWithNoMatch() throws Exception {
    final String oldValue = System.getProperty( "sun.java.command" ) ;
    try {
      System.setProperty( "sun.java.command", "-" ) ;
      new SystemPropertiesDiagnostic().print( new StringWriter() ) ;
      System.setProperty( "sun.java.command", "" ) ;
      new SystemPropertiesDiagnostic().print( new StringWriter() ) ;
    } finally {
      System.setProperty( "sun.java.command", oldValue ) ;
    }

  }
}
