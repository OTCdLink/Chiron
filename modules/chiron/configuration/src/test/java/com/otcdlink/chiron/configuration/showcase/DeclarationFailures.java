package com.otcdlink.chiron.configuration.showcase;

import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.ConfigurationTools;
import com.otcdlink.chiron.configuration.DeclarationException;
import com.otcdlink.chiron.configuration.Sources;
import org.junit.Test;

import static com.otcdlink.chiron.configuration.Sources.newSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class DeclarationFailures {

  @SuppressWarnings( "UnusedDeclaration" )
  public interface Simple extends Configuration {
    Integer number() ;
  }

  @Test
  public void unparseable() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;

    try {
      factory.create( Sources.newSource( "number = unparseable" ) ) ;
      fail( "Should have thrown an exception" ) ;
    } catch ( final DeclarationException e ) {
      assertThat( e.getMessage() ).isEqualTo(
          "\nConversion failed on property 'number': java.lang.NumberFormatException, For "
              + "input string: \"unparseable\""
      ) ;
    }
  }

  @Test
  public void sourceWithUndefinedProperty() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;

    try {
      factory.create( Sources.newSource( "unknown = -" ) ) ;
      fail( "Should have thrown an exception" ) ;
    } catch ( final DeclarationException e ) {
      assertThat( e.getMessage() ).contains( "Unknown property name 'unknown'" ) ;
    }
  }



  @Test
  public void missingValue() throws Exception {
    final Configuration.Factory< Simple > factory = ConfigurationTools.newFactory( Simple.class ) ;

    try {
      factory.create( newSource( "" ) ) ;
      fail( "Should have thrown an exception" ) ;
    } catch ( final DeclarationException e ) {
      System.out.println( e.getMessage() ) ;
      assertThat( e.getMessage() ).contains( "[ number ] No value set" ) ;
    }
  }

}
