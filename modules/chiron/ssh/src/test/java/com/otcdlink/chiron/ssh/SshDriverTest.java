package com.otcdlink.chiron.ssh;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore( "Requires a properly configured SSH server to connect to" )
public class SshDriverTest {

  @Test
  public void justRun() throws Exception {
    sshDriver5.setup( new SshDriver.Setup(
        "sleep 0.1 && echo 'Bash rulez' && echo 'Yo stderr' 1>&2 && exit 113",
        null,
        false,  // Don't merge stderr into stdout.
        "demo"
    ) ) ;

    sshDriver5.run() ;
    sshDriver5.startFuture().get() ;
    LOGGER.info( "Start future complete, process started." ) ;
    sshDriver5.terminationFuture().join() ;

    assertThat( sshDriver5.terminationFuture().get() ).isEqualTo( 113 ) ;
    assertThat( sshDriver5.stdoutLines() ).contains( "Bash rulez" ) ;
    assertThat( sshDriver5.stderrLines() ).contains( "Yo stderr" ) ;
  }

  @Test
  public void stdin() throws Exception {
    sshDriver5.setup( new SshDriver.Setup(
        "read value && echo $value",
        null, true, "demo"
    ) ) ;

    sshDriver5.start().join() ;
    sshDriver5.writeOnRemoteStdin( "Here it is" ) ;

    sshDriver5.terminationFuture().join() ;
    assertThat( sshDriver5.stdoutLines() ).contains( "Here it is" ) ;
  }

  @Test
  public void runABitLonger() throws Exception {
    sshDriver5.setup( new SshDriver.Setup(
        "sleep 0.1",
        null, true, "demo"
    ) ) ;

    sshDriver5.run().join() ;
    assertThat( sshDriver5.terminationFuture().get() ).isEqualTo( 0 ) ;
  }

  @Test
  public void reuse() throws Exception {
    sshDriver5.setup( new SshDriver.Setup(
        "echo 'Run'",
        null, true, "demo"
    ) ); ;

    sshDriver5.run().join() ;
    sshDriver5.run().join() ;
    assertThat( sshDriver5.stdoutLines() ).isEqualTo( Arrays.asList( "Run", "Run" ) ) ;
  }

  @Test( timeout = 2000 )
  public void stop() throws Exception {
    sshDriver5.setup( new SshDriver.Setup(
        "sleep 100000",
        null, true, "demo"
        // Really needed to not leave process running.
    ) ); ;

    sshDriver5.start().join() ;
    sshDriver5.stop().join() ;
    assertThat( sshDriver5.terminationFuture().get() ).isNull() ;
  }

  @Test
  public void extensionPoints() throws Exception {
    final Semaphore customInitialize = new Semaphore( 0 ) ;
    final Semaphore customStart = new Semaphore( 0 ) ;
    final Semaphore customStop = new Semaphore( 0 ) ;

    final PrivateSshDriver sshDriver5 = new PrivateSshDriver() {
      @Override
      protected void customInitialize() throws Exception {
        super.customInitialize() ;
        LOGGER.info( "Hook on #customInitialize successful." ) ;
        customInitialize.release() ;
      }

      @Override
      protected void customStart() throws Exception {
        super.customStart() ;
        LOGGER.info( "Hook on #customStart successful." ) ;
        customStart.release() ;
      }

      @Override
      protected void customEffectiveStop() throws Exception {
        LOGGER.info( "Hook on #customStop successful." ) ;
        super.customEffectiveStop() ;
        customStop.release() ;
      }
    } ;

    sshDriver5.setup( new SshDriver.Setup( "echo \"That's all!\"", null, true, "demo" ) ) ;
    sshDriver5.run() ;

    customInitialize.acquire() ;
    customStart.acquire() ;
    customStop.acquire() ;
  }

  @Test
  public void detectStart() throws Exception {
    final Semaphore doneCreating = new Semaphore( 0 ) ;
    final PrivateSshDriver sshDriver5 = new PrivateSshDriver() {
      @Override
      protected void customStart() throws Exception {
        doneCreating.release() ;
        super.customStart() ;  // Let standard start detection happen.
      }
    } ;
    sshDriver5.setup( new SshDriver.Setup(
        "read value && echo $value && sleep 1",
        "go!"::equals,
        true,
        "demo"
    ) ) ;

    sshDriver5.start() ;
    doneCreating.acquire() ;
    sshDriver5.writeOnRemoteStdin( "go!" ) ;
    sshDriver5.startFuture().join() ;

    sshDriver5.terminationFuture().join() ;  // Avoids polluting other test's log.
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( SshDriverTest.class ) ;

  public SshDriverTest() throws Exception { }

  private final PrivateSshDriver sshDriver5 = new PrivateSshDriver() ;

}