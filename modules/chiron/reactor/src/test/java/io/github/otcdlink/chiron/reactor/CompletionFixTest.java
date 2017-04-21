package io.github.otcdlink.chiron.reactor;

import io.github.otcdlink.chiron.toolbox.ToStringTools;
import org.junit.Test;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CompletionFixTest {

  @Test( timeout = 1000 )
  public void waitUntilComplete() throws Exception {
    final CompletionFix< MyEvent > completionFix = new CompletionFix<>() ;
    final Semaphore startSemaphore = new Semaphore( 0 ) ;
    final Semaphore completionSemaphore = new Semaphore( 0 ) ;
    final Thread waitingThread = createWaitingThread(
        completionFix, startSemaphore, completionSemaphore ) ;

    completionFix.start() ;
    completionFix.inbound() ;

    waitingThread.start() ;
    startSemaphore.acquire() ;

    assertThat( completionSemaphore.availablePermits() ).isEqualTo( 0 ) ;

    completionFix.outbound( new MyEvent() ) ;
    completionSemaphore.acquire() ;

  }

  @Test
  public void coalesceIdenticalEvents() throws Exception {
    final MyEvent event1 = new MyEvent() ;
    final MyEvent event2 = new MyEvent() ;
    final CompletionFix< MyEvent > completionFix = new CompletionFix<>() ;
    final Semaphore startSemaphore = new Semaphore( 0 ) ;
    final Semaphore completionSemaphore = new Semaphore( 0 ) ;
    final Thread waitingThread = createWaitingThread(
        completionFix, startSemaphore, completionSemaphore ) ;

    completionFix.start() ;
    completionFix.inbound() ;
    completionFix.inbound() ;

    waitingThread.start() ;
    startSemaphore.acquire() ;

    completionFix.outbound( event1 ) ;
    completionFix.outbound( event1 ) ;
    assertThat( completionSemaphore.availablePermits() ).isEqualTo( 0 ) ;

    completionFix.outbound( event2 ) ;

    completionSemaphore.acquire() ;
  }

// =======
// Fixture
// =======

  private static Thread createWaitingThread(
      final CompletionFix completionFix,
      final Semaphore startSemaphore,
      final Semaphore completionSemaphore
  ) {
    return new Thread(
        () -> {
          try {
            startSemaphore.release() ;
            completionFix.waitForCompletion( 1, TimeUnit.SECONDS ) ;
            completionSemaphore.release() ;
          } catch( final InterruptedException e ) {
            throw new RuntimeException( e ) ;
          }
        },
        ""
    );
  }

  private static class MyEvent {
    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) ;
    }
  }
}