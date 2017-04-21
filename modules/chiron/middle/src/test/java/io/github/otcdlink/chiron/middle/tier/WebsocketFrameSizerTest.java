package io.github.otcdlink.chiron.middle.tier;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WebsocketFrameSizerTest {

  @Test
  public void normal() throws Exception {
    final WebsocketFrameSizer sizer = WebsocketFrameSizer.tightSizer( 8192 ) ;
    assertThat( sizer.maximumPayloadSize ).isEqualTo( 8192 ) ;
    assertThat( sizer.fragmentSize ).isEqualTo( 8200 ) ;
    assertThat( sizer.maximumAggregatedSize ).isEqualTo( 8200 ) ;
  }

  @Test
  public void missingTwoByteHeader() throws Exception {
    assertThatThrownBy( () -> WebsocketFrameSizer.explicitSizer( 1, 1 ) )
        .isInstanceOf( IllegalArgumentException.class ) ;
  }

  @Test
  public void checkSizing() throws Exception {
    assertThat( WebsocketFrameSizer.frameSize( 125 ) ).isEqualTo( 131 ) ;
    assertThat( WebsocketFrameSizer.frameSize( 126 ) ).isEqualTo( 134 ) ;
    assertThat( WebsocketFrameSizer.frameSize( 8192 ) ).isEqualTo( 8200 ) ;

    assertThat( WebsocketFrameSizer.payloadSize( 131 ) ).isEqualTo( 125 ) ;
    assertThat( WebsocketFrameSizer.payloadSize( 134 ) ).isEqualTo( 126 ) ;
    assertThat( WebsocketFrameSizer.payloadSize( 8200 ) ).isEqualTo( 8192 ) ;
  }


}