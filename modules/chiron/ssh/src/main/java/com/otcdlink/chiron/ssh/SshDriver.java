package com.otcdlink.chiron.ssh;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.otcdlink.chiron.toolbox.service.AbstractService;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.Channel;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Signal;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.xfer.InMemorySourceFile;
import net.schmizz.sshj.xfer.scp.SCPDownloadClient;
import net.schmizz.sshj.xfer.scp.SCPUploadClient;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Executes a command through SSH.
 *
 * <h1>Tips and tricks</h1>
 *
 * <h2>Find available ciphers with nmap</h2>
 * <a href="https://superuser.com/a/1219759/511199">(From StackOverflow)</a>
 * <pre>
   nmap --script ssh2-enum-algos -sV -p 22 192.168.100.110
   </pre>
 *
 * <h2>Find SSH Client available ciphers</h2>
 * <a href="https://superuser.com/a/869005/511199">(From StackOverflow)</a>
 * <pre>
   ssh -Q cipher
   ssh -Q mac
   ssh -Q kex
   </pre>
 *
 * <h2>List identities known by ssh-agent</h2>
 * <a href="https://www.reddit.com/r/osx/comments/52zn5r/difficulties_with_sshagent_in_macos_sierra/d84ocwo/" >(From Reddit)</a>
 * <pre>
   ssh-add -l
   </pre>
 */
public class SshDriver< SETUP extends SshDriver.Setup >
    extends AbstractService< SETUP, Integer >
{

  protected final SshService sshService ;

  /**
   *
   */
  public SshDriver(
      final Logger logger,
      final SshService sshService
  ) {
    super( logger, "ssh" ) ;
    this.sshService = checkNotNull( sshService ) ;
  }

  @Override
  protected void enrichToString( StringBuilder stringBuilder ) {
    stringBuilder.append( sshService.remoteHost().asString() ) ;
  }


  public static class Setup {

    /**
     * Default command line.
     *
     * @see SshDriver#createCommandLine()
     */
    final String processCommandLine ;

    public final String role ;

    /**
     * Optional stdout scrutation to detect that process is truly {@link State#STARTED}.
     * It relies on default implementation of {@link #customStart()} and
     * {@link #onStdoutLine(String)}.
     *
     * <h1>{@link SshDriver} implementation note</h1>
     * {@link SshDriver}'s implementation should use {@code ExecutionContext#startupEvaluator}
     * instead, which is cleared after a successful detection.
     */
    public final Predicate< String > startupEvaluator ;

    /**
     * When set to {@code true} sshd automatically kills the remote process when it detects the
     * end of the SSH session. Using a PTY has the other (undesirable) effect to merge stderr
     * into stdout as explained in <a href="https://linux.die.net/man/7/pty">pty documentation</a>.
     */
    public final boolean allocatePty ;

    public Setup(
        final String processCommandLine,
        final Predicate< String > startupEvaluator,
        final boolean allocatePty,
        final String role
    ) {
      checkArgument( ! Strings.isNullOrEmpty( processCommandLine ) ) ;
      this.processCommandLine = processCommandLine ;
      this.startupEvaluator = startupEvaluator ;
      this.allocatePty = allocatePty ;
      this.role = checkNotNull( role ) ;
    }

  }

  protected class ExecutionContext
      extends AbstractService.ExecutionContext< SETUP, Integer >
  {
    Session sshSession = null ;
    Session.Command remoteProcessCommand = null ;
    Writer stdinWriter = null ;

    ThreadedLineProcessor stdoutLineProcessor = null ;
    ThreadedLineProcessor stderrLineProcessor = null ;
    Thread sessionWaitingThread = null ;

    /**
     * Cleared after successful detection.
     */
    volatile Predicate< String > startupEvaluator = null ;

    protected ExecutionContext( final SETUP setup, final boolean firstStart ) {
      super( setup, firstStart ) ;
      startupEvaluator = setup.startupEvaluator ;
    }
  }

  @Override
  protected AbstractService.ExecutionContext< SETUP, Integer > newExecutionContext(
      final SETUP setup,
      final boolean firstStart
  ) {
    return ( AbstractService.ExecutionContext< SETUP, Integer > )
        new ExecutionContext( setup, firstStart ) ;
  }

  private ExecutionContext executionContext() {
    return ( ExecutionContext ) bareExecutionContext() ;
  }

// =====
// Start
// =====


  @Override
  protected void customInitialize() throws Exception {
    startRemoteProcessAndConnectStreams() ;
  }

  @Override
  protected void customStart() throws Exception {
    if( setup().startupEvaluator == null ) {
      customStartComplete() ;
    }
  }

  protected String createCommandLine() throws Exception {
    return setup().processCommandLine ;
  }

  protected final void startRemoteProcessAndConnectStreams() throws Exception {
    synchronized( lock ) {
      checkState( state() == State.INITIALIZING ) ;
      ExecutionContext executionContext = executionContext() ;
      executionContext.sshSession = sshService.newSession() ;

      if( setup().allocatePty ) {
        allocatePty( executionContext.sshSession ) ;
      }

      final String processCommand = createCommandLine() ;
      logger.info( "Starting remote " + setup().role + " process with:\n  " +
          processCommand ) ;

      executionContext.remoteProcessCommand =
          executionContext.sshSession.exec( processCommand ) ;

      executionContext.stdoutLineProcessor = plugLoggingLineReader(
          executionContext.remoteProcessCommand.getInputStream(),
          this::onStdoutLine,
          "stdout"
      ) ;
      executionContext.stdoutLineProcessor.start() ;

      if( ! setup().allocatePty ) {
        executionContext.stderrLineProcessor = plugLoggingLineReader(
            executionContext.remoteProcessCommand.getErrorStream(),
            this::onStderrLine,
            "stderr"
        ) ;
        executionContext.stderrLineProcessor.start() ;
      }


      executionContext.stdinWriter = new OutputStreamWriter(
          executionContext.remoteProcessCommand.getOutputStream() ) ;

      startThreadWaitingForRemoteProcessEnd( executionContext ) ;

      logger.info( "Process '" + setup().role + "' started." ) ;

    }
  }

  /**
   * Called by default implementation of {@link #onStdoutLine(String)}
   * if {@link Setup#startupEvaluator} detected the startup.
   * Subclasses can hook here.
   */
  protected void startupDetectedOnStdout() {
    customStartComplete() ;
  }



  /**
   * https://tools.ietf.org/html/rfc4254#section-8
   */
  private static void allocatePty( final Session sshSession )
      throws ConnectionException, TransportException
  {
    sshSession.allocatePTY(
        "vt100", 80, 24, 0, 0,
        ImmutableMap.of(
            PTYMode.ECHO, 0  // Don't want to see input reappear on stdout.
        )
    ) ;
  }


// ====
// Stop
// ====

  @Override
  protected void customExplicitStop() throws Exception {
    sendKillSignalToRemoteProcess() ;
  }

  @Override
  protected void customEffectiveStop() throws Exception {
    customEffectiveStopComplete() ;
  }

  /**
   * Terminates the SSH session and disconnects the streams.
   * The thread started by {@link #startThreadWaitingForRemoteProcessEnd(ExecutionContext)}
   * will pursue the stop.
   */
  protected final void sendKillSignalToRemoteProcess() {
    final Signal kill = Signal.KILL ;
    final boolean interrupt ;
    logger.debug( "Sending " + kill + " signal to remote process ..." ) ;
    final ExecutionContext executionContext ;
    synchronized( lock ) {
      executionContext = executionContext() ;
      if( state() == State.STOPPING ) {
        try {
          executionContext.remoteProcessCommand.signal( kill ) ;
        } catch( TransportException e ) {
          logger.error( "Failed to send signal " + kill + " to remote process", e ) ;
        }
        interrupt = true ;
        /** Let {@link #startThreadWaitingForRemoteProcessEnd(ExecutionContext)} do its job. */
      } else {
        interrupt = false ;
      }
    }
    if( interrupt ) {
      // Explicit stop avoids logging uninteresting errors.
      stopLineProcessors() ;
      executionContext.sessionWaitingThread.interrupt() ;
    }
  }

  private void startThreadWaitingForRemoteProcessEnd( final ExecutionContext executionContext ) {
    final Thread sessionWaitingThread = threadFactory( "session" ).newThread(
        () -> waitForRemoteProcessEnd( executionContext ) ) ;
    executionContext.sessionWaitingThread = sessionWaitingThread ;
    sessionWaitingThread.start() ;
  }

  /**
   * Blocks until {@link ExecutionContext#remoteProcessCommand} ends (because of a premature
   * {@link #stop()} or because of natural termination of the remote process), then performs
   * post-stop related cleanup, including calling {@link #customEffectiveStop()} (which must
   * eventually call {@link #customEffectiveStopComplete()}).
   */
  private void waitForRemoteProcessEnd( final ExecutionContext executionContext ) {
    boolean stopping = false ;
    try {
      logger.debug( "Waiting (blocking wait) for SSH session to end ..." ) ;
      /** Interrupting the thread causes {@link Channel#join()} to fail immediately.*/
      executionContext.remoteProcessCommand.join() ;
      logger.info( "End reached for " + executionContext.remoteProcessCommand + "." );
    } catch( ConnectionException e ) {
      synchronized( lock ) {
        stopping = state() == State.STOPPING ;
      }
      if( ! stopping ) {
        logger.error( "Could not connect.", e ) ;
      }
    }
    try {
      synchronized( lock ) {
        if( state() == State.STARTED ) {
          state( State.STOPPING ) ;
        }
        if( state() == State.STOPPING ) {
          try {
            /** Already done if {@link #stop()} was called. */
            stopLineProcessors() ;

            /** Close the session anyway. Depending on {@link Setup#allocatePty}, this can cause
             * automatic termination of the remote process. */
            executionContext.remoteProcessCommand.close() ;
          } finally {
            // May be null if something got wrong (brutal termination, process not started ...)
            completion( executionContext.remoteProcessCommand.getExitStatus() ) ;
            final String exitErrorMessage =
                executionContext.remoteProcessCommand.getExitErrorMessage() ;
            if( exitErrorMessage != null ) {
              logger.debug( "Exit error message: '" + exitErrorMessage + "'." ) ;
            }
          }
        }
      }
    } catch( TransportException | ConnectionException e ) {
      if( ! stopping ) {
        logger.error( "Error while closing " + this.executionContext().sshSession + ".", e ) ;
      }
    } finally {
      try {
        customEffectiveStop() ;
      } catch( Exception e ) {
        logger.error( "Error while stopping.", e ) ;
      }
    }
  }

  private void stopLineProcessors() {
    final ExecutionContext executionContext = executionContext() ;
    executionContext.stdoutLineProcessor.stop() ;
    if( ! setup().allocatePty ) {
      executionContext.stderrLineProcessor.stop() ;
    }
  }


// ======================
// Remote process streams
// ======================


  private ThreadedLineProcessor plugLoggingLineReader(
      final InputStream inputStream,
      final Consumer< String > lineConsumer,
      final String purpose
  ) {
    return new ThreadedLineProcessor(
        inputStream,
        threadFactory( purpose ),
        Charsets.UTF_8,
        lineConsumer
    ) ;
  }

  protected void onStdoutLine( final String line ) {
    logger.debug( "1> " + line ) ;
    final ExecutionContext executionContext = executionContext() ;
    final Predicate< String > startupEvaluator = executionContext.startupEvaluator ;
    if( startupEvaluator != null ) {
      if( startupEvaluator.test( line ) ) {
        executionContext.startupEvaluator = null ;
        logger.debug( "Startup detected." ) ;
        startupDetectedOnStdout() ;
      }
    }
  }

  protected void onStderrLine( final String line ) {
    logger.warn( "2> " + line ) ;
  }

  protected final void writeOnRemoteStdin( final String line ) {
    try {
      synchronized( lock ) {
        checkState(
            ! ImmutableSet.of( State.NEW, State.INITIALIZING, State.STARTING )
                .contains( state() )
        ) ;
        final ExecutionContext executionContext = executionContext() ;
        executionContext.stdinWriter.write( line + LineBreak.CR_UNIX.asString ) ;
        executionContext.stdinWriter.flush() ;
      }
      logger.debug( "<0 " + line ) ;
    } catch( final IOException e ) {
      logger.error( "Could not write on remote " + setup().role + "process' stdin", e ) ;
    }
  }



// =============
// Miscellaneous
// =============

  protected final ThreadFactory threadFactory( final String purpose ) {
    return threadFactory( setup().role, purpose ) ;
  }

  /**
   * Useful to create a remote file using SCP, without creating a file on local filesystem.
   */
  public static InMemorySourceFile createInMemorySourceFile(
      final String fileName,
      final String contentAsString
  ) {
    final String preDaybreakSnapshotAsString = contentAsString ;
    final byte[] bytes = preDaybreakSnapshotAsString.getBytes( Charsets.UTF_8 ) ;
    final ByteSource byteSource = ByteSource.wrap( bytes ) ;
    return new InMemorySourceFile() {
      @Override
      public String getName() {
        return fileName ;
      }

      @Override
      public long getLength() {
        return bytes.length ;
      }

      @Override
      public InputStream getInputStream() throws IOException {
        return byteSource.openStream() ;
      }
    } ;
  }

  protected final SCPUploadClient createScpUploadClient() {
    return sshService.newScpFileTransfer().newSCPUploadClient() ;
  }

  protected final SCPDownloadClient createScpDownloadClient() {
    return sshService.newScpFileTransfer().newSCPDownloadClient() ;
  }

}
