package com.otcdlink.chiron.toolbox.concurrent;

import com.otcdlink.chiron.toolbox.netty.NettyTools;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.internal.PlatformDependent;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorPartitionerTest {


  @Test
  public void simpleRun() throws Exception {
    final ExecutorPartitioner< Integer > integerExecutorPartitioner = newExecutorPartitioner( 2 ) ;
    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    integerExecutorPartitioner.execute(
        1,
        () -> {
          LOGGER.info( "Task executed." ) ;
          doneSemaphore.release() ;
        }
    ) ;
    doneSemaphore.acquire() ;
  }

  @Test
  public void callable() throws Exception {
    final ExecutorPartitioner< Integer > integerExecutorPartitioner = newExecutorPartitioner( 2 ) ;
    final Future< String > future = integerExecutorPartitioner.submit(
        1,
        () -> {
          LOGGER.info( "Task executed." ) ;
          return "Done" ;
        }
    ) ;
    assertThat( future.get() ).isEqualTo( "Done" ) ;
  }

  @Test
  public void exceptionalRun() throws Exception {
    final Semaphore doneSemaphore = new Semaphore( 0 ) ;
    final ExecutorPartitioner< Integer > integerExecutorPartitioner = newExecutorPartitioner(
        2,
        SIMPLE_KEY_HASHER,
        doneSemaphore
    ) ;
    integerExecutorPartitioner.execute( 1, () -> { throw new RuntimeException( "Boom" ) ; } ) ;
    doneSemaphore.acquire() ;
  }


  @Test
  public void manyRuns() throws Exception {
    final int partitionCount = 10 ;
    final int keyCount = 100 ;
    final AtomicInteger[] partitionCounters = new AtomicInteger[ partitionCount ] ;
    Arrays.parallelSetAll( partitionCounters, Ø -> new AtomicInteger() ) ;
    final Function< Integer, Integer > keyHasher = i -> i / 10 ;
    final ExecutorPartitioner< Integer > executorPartitioner = newExecutorPartitioner(
        partitionCount,
        keyHasher,
        new Semaphore( 0 )
    ) ;

    final AtomicInteger taskCompletionCounter = new AtomicInteger( 0 ) ;
    final Semaphore doneSemaphore = new Semaphore( 0 ) ;

    for( int i = 0 ;  i < keyCount ; i ++ ) {
      final int index = i ;
      executorPartitioner.execute( index, () -> {
        final int hash = keyHasher.apply( index ) ;
        partitionCounters[ hash ].getAndIncrement() ;
        LOGGER.info( "Task complete for " + index + "." ) ;
        if( taskCompletionCounter.incrementAndGet() == keyCount ) {
          doneSemaphore.release() ;
        }
      } ) ;
    }

    doneSemaphore.acquire() ;
    final int expectedCounterValue = keyCount / partitionCount ;
    for( int i = 0 ; i < partitionCounters.length ; i ++ ) {
      final int counterValue = partitionCounters[ i ].get() ;
      LOGGER.info( "partitionCounters[ " + i + " ]=" + counterValue + "." ) ;
    }
    for( int i = 0 ; i < keyCount ; i ++ ) {
      final int partitionIndex = keyHasher.apply( i ) ;
      assertThat( partitionCounters[ partitionIndex ].get() ).isEqualTo( expectedCounterValue ) ;
    }

  }



// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ExecutorPartitioner.class ) ;

  private static ExecutorPartitioner< Integer > newExecutorPartitioner(
      final int partitionCount
  ) {
    return newExecutorPartitioner(
        partitionCount, SIMPLE_KEY_HASHER,
        new Semaphore( 0 )
    ) ;
  }


  private static ExecutorPartitioner< Integer > newExecutorPartitioner(
      final int partitionCount,
      final Function< Integer, Integer > keyHasher,
      final Semaphore throwableCaughtSemaphore
  ) {
    final AtomicInteger threadCounter = new AtomicInteger() ;
    final NioEventLoopGroup executor = new NioEventLoopGroup(
        0,
        runnable -> {
          final Thread thread = new Thread( runnable ) ;
          thread.setName( "eventloop-" + threadCounter.getAndIncrement() ) ;
          thread.setDaemon( true ) ;
          thread.setUncaughtExceptionHandler( ( emittingThread, throwable ) -> {
            LOGGER.error( "Caught some Throwable", throwable ) ;
            throwableCaughtSemaphore.release() ;
          } ) ;
          return thread ;
        }
    ) ;

    return new ExecutorPartitioner<>(
        partitionCount,
        executor,
        PlatformDependent::newMpscQueue,  // Default size is 1024^2.
        keyHasher,
        i -> Integer.toString( i )
    ) ;
  }

  private static final Function< Integer, Integer > SIMPLE_KEY_HASHER = i -> i ;

  static {
    NettyTools.forceNettyClassesToLoad() ;
    LOGGER.info( "**** Test begins here ****" ) ;
  }


}