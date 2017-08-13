package io.github.caillette.shoemaker.upend.twilio;

import com.otcdlink.chiron.middle.PhoneNumber;
import com.otcdlink.chiron.upend.twilio.DefaultTokenPackFactory;
import com.otcdlink.chiron.upend.twilio.TokenPack;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultTokenPackFactoryTest {
  @Test
  public void formats() throws Exception {
    final DefaultTokenPackFactory factory = new DefaultTokenPackFactory() ;
    final TokenPack tokenPack = factory.createNew( new PhoneNumber( "+1" ) ) ;

    assertThat( tokenPack.signonEnrichmentToken().asString() ).matches( "AUL-[0-9A-Za-z]{12}?" ) ;
    assertThat( tokenPack.urlToken() ).matches( "AUT-[0-9A-Za-z]{16}?" ) ;
    assertThat( tokenPack.tokenExpectedFromUser().asString() ).matches( "[0-9]{6}?" ) ;

  }
}
