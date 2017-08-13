package com.otcdlink.chiron.toolbox;

import com.google.common.reflect.TypeToken;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DelegatorTest {

  @Test
  public void calling() throws IOException {
    final Delegator< Readable > delegator = Delegator.create( Readable.class ) ;
    final Readable proxiedReadable = delegator.getProxy() ;

    final CharBuffer charBuffer = CharBuffer.allocate( 1 ) ;
    final Readable delegate = Mockito.mock( Readable.class ) ;
    Mockito.when( delegate.read( charBuffer ) ).thenReturn( 1 ) ;

    delegator.setDelegate( delegate ) ;
    final int read = proxiedReadable.read( charBuffer ) ;

    assertThat( read ).isEqualTo( 1 ) ;
    Mockito.verify( delegate ).read( charBuffer ) ;

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
  public void cannotSetDelegateTwice() throws IOException {
    final Delegator< Readable > delegator = Delegator.create( Readable.class ) ;
    delegator.setDelegate( Mockito.mock( Readable.class ) ) ;
    delegator.setDelegate( Mockito.mock( Readable.class ) ) ;
  }

}
