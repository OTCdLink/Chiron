package io.github.otcdlink.chiron.ssh;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.ssh.hello.EndurancePrint;
import io.github.otcdlink.chiron.ssh.hello.JustHello;
import io.github.otcdlink.chiron.ssh.synchronizer.LocalFileEnumerator;
import io.github.otcdlink.chiron.ssh.synchronizer.ProjectDirectoryConstants;
import io.github.otcdlink.chiron.toolbox.ObjectTools;
import io.github.otcdlink.chiron.toolbox.security.KeystoreTools;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore( "Requires a properly configured SSH server to connect to" )
public class SshJavaDriverTest {

  @Test
  public void justHello() throws Exception {
    setupSshJavaDriver( javaSshDriver4, JustHello.class, ImmutableList.of( "one", "two" ) ) ;
    javaSshDriver4.run().join() ;
    assertThat( javaSshDriver4.stdoutLines() )
        .isEqualTo( Collections.singletonList( "Hello one two" ) ) ;
  }

  @Test
  public void endurance() throws Exception {
    final AtomicInteger counterExpectation = new AtomicInteger() ;
    final ObjectTools.Holder< CompletableFuture > completion = ObjectTools.newHolder() ;
    final PrivateSshJavaDriver hookedSshJavaDriver = new PrivateSshJavaDriver() {
      @Override
      protected void onStdoutLine( String line ) {
        LOGGER.info( "1> " + line ) ;
        final Iterator< String > tokenIterator = Splitter.on( ' ' ).split( line ).iterator() ;
        tokenIterator.next() ;
        final int counterObtained = Integer.parseInt( tokenIterator.next() ) ;
        final int expectedCounterValue = counterExpectation.getAndIncrement() ;
        if( counterObtained != expectedCounterValue ) {
          final String message = "Counter discrepancy, expected " + expectedCounterValue + "." ;
          LOGGER.error( message ) ;
          completion.get().completeExceptionally( new IllegalArgumentException( message ) ) ;
        }
      }
    } ;

    final int delayMs = 100 ;
    final int expectedDurationMs = 100_000 ;
    final int iterationCount = expectedDurationMs / delayMs ;

    setupSshJavaDriver(
        hookedSshJavaDriver,
        EndurancePrint.class,
        ImmutableList.of( Integer.toString( iterationCount ), Integer.toString( delayMs ) )
    ) ;

    completion.set( hookedSshJavaDriver.run() ) ;
    completion.get().join() ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( SshJavaDriverTest.class ) ;

  static {
    KeystoreTools.activateJavaCryptographyExtensions() ;
  }

  private static void setupSshJavaDriver(
      final PrivateSshJavaDriver javaSshDriver4,
      final Class javaClass,
      final ImmutableList< String > programArguments
  ) {
    javaSshDriver4.setup( new SshJavaDriver.Setup(
        null,
        true,
        "endurance",
        LocalFileEnumerator.lazy( ProjectDirectoryConstants.MODULES_DIRECTORY ),
        "/usr/bin/java",
        "/home/otcdlink/remoting-ssh",
        ImmutableList.of(),
        null,
        javaClass.getName(),
        programArguments
    ) ) ;
  }



  public SshJavaDriverTest() throws Exception { }

  private final PrivateSshJavaDriver javaSshDriver4 = new PrivateSshJavaDriver() ;


}