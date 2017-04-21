package io.github.otcdlink.chiron.middle;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.buffer.BytebufTools;
import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteSequenceMatcherTest {

  @Test
  public void match() throws Exception {
    final ChannelTools.ByteSequenceMatcher matcher = new ChannelTools.ByteSequenceMatcher( "Foo" ) ;
    assertThat( matcher.matchThenSkip( reader( "Foobar" ) ) ).isTrue() ;
  }

  @Test
  public void noMatch() throws Exception {
    final ChannelTools.ByteSequenceMatcher matcher = new ChannelTools.ByteSequenceMatcher( "Foo" ) ;
    assertThat( matcher.matchThenSkip( reader( "No match" ) ) ).isFalse() ;
  }

  @Test
  public void tooShort() throws Exception {
    final ChannelTools.ByteSequenceMatcher matcher = new ChannelTools.ByteSequenceMatcher( "Foo" ) ;
    assertThat( matcher.matchThenSkip( reader( "Fo" ) ) ).isFalse() ;
  }

// =======
// Fixture
// =======

  private static PositionalFieldReader reader( final String asciiContent ) {
    final ByteBuf byteBuf = Unpooled.copiedBuffer( asciiContent.getBytes( Charsets.US_ASCII ) ) ;
    return BytebufTools.coat( byteBuf ) ;
  }


}