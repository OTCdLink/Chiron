package com.otcdlink.chiron.configuration.showcase;

import com.otcdlink.chiron.configuration.Configuration;
import com.otcdlink.chiron.configuration.DeclarationException;
import com.otcdlink.chiron.configuration.OnlineHelpTools;
import com.otcdlink.chiron.configuration.Sources;
import com.otcdlink.chiron.configuration.TemplateBasedFactory;
import org.junit.Test;

import static org.junit.Assert.fail;

public class OnlineHelp {

  public interface Simple extends Configuration {
    Integer myNumber() ;
    String myString() ;
    String myMandatoryString() ;
  }

  @Test
  public void test() throws Exception {
    final Configuration.Factory< Simple > factory ;
    factory = new TemplateBasedFactory< Simple >( Simple.class ) {
      @Override
      protected void initialize() {
        property( using.myNumber() )
            .maybeNull()
            .documentation(
                "This is just a number but we want to test word wrap after the 80th column "
                    + "so we add lots of text here.\n"
                + "We also support line breaks inside the documentation."
            )
        ;
        property( using.myString() )
            .defaultValue( "FOO" )
            .documentation( "Just a string." )
        ;
      }
    } ;

    try {
      factory.create( Sources.newSource( "bad source" ) ) ;
      fail( "Should have thrown an exception" ) ;
    } catch ( final DeclarationException e ) {
      final String help = OnlineHelpTools.errorMessageAndHelpAsString( e ) ;
      System.out.println( help ) ;
    }

  }


}
