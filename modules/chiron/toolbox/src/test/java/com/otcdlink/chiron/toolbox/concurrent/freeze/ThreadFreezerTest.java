package com.otcdlink.chiron.toolbox.concurrent.freeze;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ThreadFreezerTest {

  @Test
  public void justExecuteCommand() throws Exception {
    final PrivateService service = new PrivateService() ;
    final PrivateCommand command1 = new PrivateCommand() ;
    assertThat( command1.executionCounter() ).isEqualTo( 0 ) ;
    service.consume( command1 ) ;
    service.executePendingCommandsAndShutdown() ;
    assertThat( command1.executionCounter() ).isEqualTo( 1 ) ;
  }

  @Test
  public void freeze() throws Exception {
    final ThreadFreezer< PrivateService > threadFreezer = new ThreadFreezer<>() ;
    freeze( threadFreezer ) ;
  }

  @Test
  public void reuse() throws Exception {
    final ThreadFreezer< PrivateService > threadFreezer = new ThreadFreezer<>() ;
    freeze( threadFreezer ) ;
    freeze( threadFreezer ) ;
  }

  private static void freeze( final ThreadFreezer< PrivateService > threadFreezer )
      throws InterruptedException
  {
    final PrivateService service = new PrivateService() ;
    final PrivateCommand lockCommand = new LockCommand( threadFreezer.asConsumer() ) ;
    final PrivateCommand command2 = new PrivateCommand() ;
    service.consume( lockCommand ) ;
    service.consume( command2 ) ;
    assertThat( lockCommand.executionCounter() ).isEqualTo( 0 ) ;
    assertThat( command2.executionCounter() ).isEqualTo( 0 ) ;
    final ThreadFreeze< PrivateService > threadFreeze = threadFreezer.freeze() ;
    assertThat( command2.executionCounter() ).describedAs( "Thread is blocked." ).isEqualTo( 0 ) ;
    assertThat( threadFreeze.frozen() ).isSameAs( service ) ;
    threadFreeze.unfreeze() ;
    service.executePendingCommandsAndShutdown() ;
    assertThat( lockCommand.executionCounter() ).isEqualTo( 1 ) ;
    assertThat( command2.executionCounter() ).isEqualTo( 1 ) ;
  }

  @Test
  public void badStateForLocking() throws Exception {
    assertThatThrownBy( () -> new ThreadFreezer().freeze() )
        .describedAs( "Should request a Consumer first." )
        .isInstanceOf( IllegalStateException.class )
    ;
  }

// =======
// Fixture
// =======

  private static class PrivateService implements Freezable<PrivateService> {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor() ;

    @Override
    public String toString() {
      return ToStringTools.nameAndCompactHash( this ) + "{}" ;
    }

    public void consume( final PrivateCommand command ) {
      executorService.submit( () -> command.execute( this ) ) ;
    }

    public void executePendingCommandsAndShutdown() throws InterruptedException {
      executorService.shutdown() ;
      executorService.awaitTermination( 1, TimeUnit.MINUTES ) ;
    }

    @Override
    public void freeze( final Consumer<PrivateService> freezer ) {
      freezer.accept( this ) ;
    }
  }

  private static class PrivateCommand {

    private final AtomicInteger executionCounter = new AtomicInteger() ;

    public void execute( final PrivateService service ) {
      doExecute( service ) ;
      executionCounter.getAndIncrement() ;
    }

    protected void doExecute( final PrivateService service ) { }

    public int executionCounter() {
      return executionCounter.get() ;
    }

  }

  private static class LockCommand extends PrivateCommand {

    private final Consumer<PrivateService> serviceConsumer ;

    private LockCommand( final Consumer<PrivateService> serviceConsumer ) {
      this.serviceConsumer = checkNotNull( serviceConsumer ) ;
    }

    @Override
    protected void doExecute( final PrivateService service ) {
      service.freeze( serviceConsumer ) ;
    }
  }
}