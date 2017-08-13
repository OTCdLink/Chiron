package com.otcdlink.chiron.toolbox.concurrent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorProxifierTest {

  @Test
  public void singleThreadExecutor() throws Exception {
    LOGGER.info( "This is test health." ) ;
    final BlockingQueue< Thread > threadCaptor = new ArrayBlockingQueue<>( 1 ) ;
    executor.execute( () -> threadCaptor.add( Thread.currentThread() ) ) ;
    assertThat( threadCaptor.take() ).isSameAs( executor.thread ) ;
  }

  @Test
  public void proxify() throws Exception {
    final ConcreteObject original = new ConcreteObject() ;
    final Object proxified = ExecutorProxifier.proxify( executor, original ).proxy() ;
    ( ( Foo ) proxified ).foo() ;
    ( ( Bar ) proxified ).bar( "x" ) ;
    executor.waitForPendingTasksToExecute() ;
    assertThat( original.methodCalls ).containsExactly( "foo", "bar x" ) ;
    assertThat( original.callingThreads ).containsExactly( executor.thread, executor.thread ) ;
  }

  @Test( expected = IllegalArgumentException.class )
  public void badInterface() throws Exception {
    ExecutorProxifier.proxify( executor, ( Bad ) () -> 0 ) ;
  }

  // =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( ExecutorProxifierTest.class ) ;

  private interface Foo {
    void foo() ;
  }

  private interface Bar {
    void bar( String s ) ;
  }

  private interface Bad {
    int bad() ;
  }

  private static final class ConcreteObject implements Foo, Bar {
    private final List< String > methodCalls = new ArrayList<>() ;
    private final List< Thread > callingThreads = new ArrayList<>() ;

    @Override
    public void foo() {
      methodCalls.add( "foo" ) ;
      callingThreads.add( Thread.currentThread() ) ;
    }

    @Override
    public void bar( final String s ) {
      methodCalls.add( "bar " + s ) ;
      callingThreads.add( Thread.currentThread() ) ;
    }
  }


// ========
// Executor
// ========

  private final SingleThreadExecutor executor = new SingleThreadExecutor() ;
  @Before
  public void setUp() throws Exception {
    executor.start() ;
  }

  @After
  public void tearDown() throws Exception {
    executor.stop() ;
  }

  /**
   * We want our own {@code Executor} so we can get a reference to the executing {@code Thread}
   * for further comparisons.
   */
  private static class SingleThreadExecutor implements Executor {

    public final Thread thread ;
    private final BlockingQueue< Runnable > taskQueue = new LinkedBlockingDeque<>() ;

    public SingleThreadExecutor() {
      thread = new Thread( () -> {
        while( ! Thread.interrupted() ) {
          try {
            final Runnable take = taskQueue.take() ;
            take.run() ;
          } catch( final InterruptedException e ) {
            break ;
          } catch( final Exception e ) {
            LOGGER.error( "Problem: ", e ) ;
          }
        }
      } ) ;
      thread.setName( SingleThreadExecutor.class.getSimpleName() ) ;
    }

    @Override
    public void execute( final Runnable task ) {
      taskQueue.add( task ) ;
    }

    public void start() {
      thread.start() ;
    }

    public void waitForPendingTasksToExecute() throws InterruptedException {
      final Semaphore semaphore = new Semaphore( 0 ) ;
      execute( semaphore::release ) ;
      semaphore.acquire() ;
    }

    public void stop() {
      thread.interrupt() ;
    }
  }


}