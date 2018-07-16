package com.otcdlink.chiron.toolbox;

import com.google.common.reflect.TypeToken;
import mockit.Expectations;
import mockit.Injectable;
import org.junit.Test;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorTest {

  @Test
  public void calling( @Injectable final Readable delegate ) throws IOException {
    final Delegator< Readable > delegator = Delegator.create( Readable.class ) ;
    final Readable proxiedReadable = delegator.getProxy() ;

    final CharBuffer charBuffer = CharBuffer.allocate( 1 ) ;
    new Expectations() {{
      delegate.read( charBuffer ) ;
      result = 1 ;
    }} ;

    delegator.setDelegate( delegate ) ;
    final int read = proxiedReadable.read( charBuffer ) ;

    assertThat( read ).isEqualTo( 1 ) ;

  }

  @Test
  public void typeToken() throws Exception {
    final Delegator< Iterable< Integer > > delegator =
        Delegator.create( new TypeToken< Iterable< Integer > >() { } ) ;
    final List< Integer > realList = Arrays.asList( 1, 2 ) ;
    delegator.setDelegate( realList ) ;

    assertThat( delegator.getProxy().iterator().next() ).isEqualTo( 1 ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void cantCallWhenDelegateNotSet() throws IOException {
    final Delegator< Readable > delegator = Delegator.create( Readable.class ) ;
    delegator.getProxy().read( null ) ;
  }

  @Test( expected = IllegalStateException.class )
  public void cannotSetDelegateTwice( @Injectable final Readable delegate ) throws IOException {
    final Delegator< Readable > delegator = Delegator.create( Readable.class ) ;
    delegator.setDelegate( delegate ) ;
    delegator.setDelegate( delegate ) ;
  }

}
