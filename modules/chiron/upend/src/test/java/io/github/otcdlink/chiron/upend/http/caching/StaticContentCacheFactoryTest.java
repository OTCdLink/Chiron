package io.github.otcdlink.chiron.upend.http.caching;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StaticContentCacheFactoryTest {

  @Test
  public void shareHappens() throws Exception {

    final BoobyTrappedBytesSource boobyTrappedBytesSource = new BoobyTrappedBytesSource() ;
    final StaticContentCache cache1 = share.sharedCache(
        ImmutableMap.of( "x", "x/x" ),
        ImmutableMap.of( "xxx.x", boobyTrappedBytesSource )
    ) ;

    final StaticContentCache cache2 = share.sharedCache(
        ImmutableMap.of( "x", "x/x" ),
        ImmutableMap.of( "xxx.x", boobyTrappedBytesSource )
    ) ;

    final String string1 = cache1.staticContent( "xxx.x" ).bytebuf().toString( CHARSET ) ;
    final String string2 = cache2.staticContent( "xxx.x" ).bytebuf().toString( CHARSET ) ;

    assertThat( string2 ).isEqualTo( string1 ).isEqualTo( "1" ) ;
  }

  @Test
  public void mimeTypeConflict() throws Exception {
    assertThatThrownBy( () -> {
      share.sharedCache( ImmutableMap.of( "x", "x/x" ), RESOURCE_MAP_1 ) ;
      share.sharedCache( ImmutableMap.of( "x", "Y/Y" ), RESOURCE_MAP_1 ) ;
    } ) .isInstanceOf( IllegalArgumentException.class )
        .hasMessageContaining( "Incompatible MIME types" )
    ;
  }
  @Test
  public void resourceMapConflict() throws Exception {
    assertThatThrownBy( () -> {
      share.sharedCache( ImmutableMap.of( "x", "x/x" ), RESOURCE_MAP_1 ) ;
      share.sharedCache( ImmutableMap.of( "x", "x/x" ), RESOURCE_MAP_1BIS ) ;
    } ) .isInstanceOf( IllegalArgumentException.class )
        .hasMessageContaining( "Incompatible resource values" )
    ;
  }

// =======
// Fixture
// =======

  private final StaticContentCacheFactory share = new StaticContentCacheFactory() ;

  private static final Charset CHARSET = Charsets.US_ASCII ;

  private static final ImmutableMap< String, ByteSource > RESOURCE_MAP_1 =
      ImmutableMap.of( "x.x", ByteSource.wrap( "XXX".getBytes( CHARSET ) ) ) ;

  private static final ImmutableMap< String, ByteSource > RESOURCE_MAP_1BIS =
      ImmutableMap.of( "x.x", ByteSource.wrap( "XXX".getBytes( CHARSET ) ) ) ;

  /**
   * A {@link ByteSource} that serves a different content each time it is opened,
   * so we detect if {@link StaticContentCacheFactory} avoids multiple openings.
   */
  private static class BoobyTrappedBytesSource extends ByteSource {
    private final AtomicInteger counter = new AtomicInteger() ;
    @Override
    public InputStream openStream() throws IOException {
      final String content = "" + counter.getAndIncrement() ;
      return new ByteArrayInputStream( content.getBytes() ) ;
    }
  }



}