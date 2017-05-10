package io.github.otcdlink.chiron.fixture.tcp;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.github.otcdlink.chiron.toolbox.text.Plural;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public final class EchoStressClient {

  private static final Logger LOGGER = LoggerFactory.getLogger( EchoStressClient.class ) ;

  private final InetSocketAddress proxyAddress ;

  private final int index ;
  private final InetSocketAddress serverAddress ;
  private final int salvoCount ;
  private final int salvoSize ;

  public EchoStressClient(
      final int index,
      final InetSocketAddress serverAddress,
      final int salvoCount,
      final int salvoSize
  ) {
    this( index, null, serverAddress, salvoCount, salvoSize ) ;
  }

  public EchoStressClient(
      final int index,
      final InetSocketAddress proxyAddress,
      final InetSocketAddress serverAddress,
      final int salvoCount,
      final int salvoSize
  ) {
    this.proxyAddress = proxyAddress ;
    checkArgument( index >= 0 ) ;
    this.index = index ;
    this.serverAddress = checkNotNull( serverAddress ) ;
    this.salvoCount = salvoCount ;
    this.salvoSize = salvoSize ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "-" + index ;
  }

  public ListenableFuture< ? > run( final Function< InetSocketAddress, EchoClient > connect ) {
    final SettableFuture< ? > terminationFuture = SettableFuture.create() ;
    final InetSocketAddress address = proxyAddress == null ? serverAddress : proxyAddress ;

    /** This is a trick to start {@link EchoClient} in a thread named from current
     * {@link EchoStressClient} so we can associate {@link EchoClient}'s details that
     * appear only in the log. */
    new Thread(
        () -> {
          final EchoClient echoClient ;
          try {
            echoClient = connect.apply( address ) ;
          } catch( final Exception exception ) {
            terminationFuture.setException( exception ) ;
            return ;
          }

          final Thread generatorThread = new Thread(
              () -> generate( echoClient, terminationFuture ),
              EchoStressClient.this.toString() + "-generator"
          ) ;

          final Thread verifierThread = new Thread(
              () -> verify( echoClient, terminationFuture ),
              EchoStressClient.this.toString() + "-verifier"
          ) ;

          Futures.addCallback( terminationFuture, new FutureCallback< Object >() {
            @Override
            public void onSuccess( @Nullable final Object result ) {
              stopQuiet() ;
            }

            @Override
            public void onFailure( @Nonnull final Throwable t ) {
              LOGGER.error( "Reported failure. Using " + echoClient + ".", t ) ;
              stopQuiet() ;
            }

            private void stopQuiet() {
              generatorThread.interrupt() ;
              verifierThread.interrupt() ;
              EchoClient.stopQuiet( echoClient ) ;
            }
          } ) ;

          generatorThread.start() ;
          verifierThread.start() ;
        },
        toString()
    ).start() ;

    return terminationFuture ;
  }

  private void generate(
      final EchoClient echoClient,
      final SettableFuture< ? > terminationFuture
  ) {
    final ByteFriendlyRandom generator = new ByteFriendlyRandom( this.index ) ;
    final byte[] writeBuffer = new byte[ salvoSize ] ;
    try {
      for( int i = 0 ; i < salvoCount ; i ++ ) {
        if( threadIsInterrupted() ) {
          break ;
        }
        generator.nextBytes( writeBuffer ) ;
        echoClient.write( writeBuffer ) ;
        if( index == 0 ) {
          LOGGER.info( "Completed " + Plural.th( i ) + " salvo from " + this + "." ) ;
        }
      }
    } catch( final Exception e ) {
      terminationFuture.setException( e ) ;
    }
  }

  private void verify( final EchoClient echoClient, final SettableFuture< ? > terminationFuture ) {
    final ByteFriendlyRandom verifier = new ByteFriendlyRandom( this.index ) ;
    final byte[] readBuffer = new byte[ salvoSize ] ;
    final long totalBytesToVerify = salvoCount * salvoSize ;
    long totalByteVerified = 0 ;
    try {
      while( totalByteVerified < totalBytesToVerify && ! threadIsInterrupted() ) {
        final int read = echoClient.read( readBuffer ) ;
        for( int i = 0 ; i < read ; i ++ ) {
          assertThat( readBuffer[ i ] )
              .describedAs( Plural.th( totalByteVerified ) + " byte from the start" )
              .isEqualTo( verifier.nextByte() )
          ;
          totalByteVerified ++ ;
        }
      }
      if( ! threadIsInterrupted() ) {
        terminationFuture.set( null ) ;
      }
    } catch( final Exception e ) {
      terminationFuture.setException( e ) ;
    }
  }

  private static boolean threadIsInterrupted() {
    return Thread.currentThread().isInterrupted() ;
  }

  /**
   * Workaround.
   * <p>
   * The two snippets below are not equivalent, probably because {@code Random} generates 4
   * bytes (size of an {@code int}) at a time:
   *
   * <pre>
   * new Random( 0 ).nextBytes( new byte[ 2 ] ) ;
   * </pre>
   *
   * <pre>
   * final Random random = new Random( 0 ) ;
   * random.nextBytes( new byte[ 1 ] ) ;
   * random.nextBytes( new byte[ 1 ] ) ;
   * </pre>
   */
  private static class ByteFriendlyRandom {

    /**
     * Deterministic generation for a given seed is the coolest thing on this earth.
     */
    private final Random random ;

    private ByteFriendlyRandom( final int seed ) {
      this.random = new Random( seed ) ;
    }

    public byte nextByte() {
      return ( byte ) random.nextInt() ;
    }

    public void nextBytes( final byte[] collector ) {
      for( int i = 0 ; i < collector.length ; i ++ ) {
        collector[ i ] = nextByte() ;
      }

    }
  }

}
