package com.otcdlink.chiron.ssh;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.ssh.synchronizer.FileKind;
import com.otcdlink.chiron.ssh.synchronizer.LocalFileEnumerator;
import com.otcdlink.chiron.ssh.synchronizer.RemoteFileEnumerator;
import com.otcdlink.chiron.ssh.synchronizer.Synchronizer;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SshJavaDriver< SETUP extends SshJavaDriver.Setup > extends SshDriver< SETUP > {

  public static class Setup extends SshDriver.Setup {

    public final String pathToJavaVirtualMachine ;
    public final String remoteBaseDirectory ;

    /**
     * Using a {@code Supplier} so we can defer directory walk until {@link #customStart()}.
     */
    public final Supplier< LocalFileEnumerator.FileBundle > localFileBundle ;

    public final RemoteFileEnumerator remoteFileEnumerator ;
    public final ImmutableList< String > systemProperties ;

    /**
     * If not {@code null}, causes addition of System Properties setting unauthenticated JMX;
     * then this happens regardless of the value of {@link #systemProperties}.
     */
    public final Integer jmxPort ;

    /**
     * Full-qualified class name.
     */
    public final String executableJavaClassName ;

    /**
     * Program arguments (appear after class name), used by default by
     * {@link SshDriver#createCommandLine()} which can be overriden.
     * <em>Then those parameters may be ignored.</em>
     */
    public final ImmutableList< String > programArguments ;


    public Setup(
        final Predicate< String > startupEvaluator,
        final boolean allocatePty,
        final String purpose,
        final Supplier< LocalFileEnumerator.FileBundle > localFileBundleSupplier,
        final String pathToJavaVirtualMachine,
        final String remoteBaseDirectory,
        final ImmutableList< String > systemProperties,
        final Integer jmxPort,
        final String executableJavaClassName,
        final ImmutableList< String > programArguments
    ) {
      super( pathToJavaVirtualMachine + " -version", startupEvaluator, allocatePty, purpose ) ;
      checkArgument( ! Strings.isNullOrEmpty( pathToJavaVirtualMachine ) ) ;
      this.pathToJavaVirtualMachine = pathToJavaVirtualMachine ;
      this.remoteBaseDirectory = checkNotNull( remoteBaseDirectory ) ;
      this.localFileBundle = checkNotNull( localFileBundleSupplier ) ;
      this.remoteFileEnumerator = new RemoteFileEnumerator( remoteBaseDirectory ) ;
      this.jmxPort = jmxPort ;
      this.systemProperties = checkNotNull( systemProperties ) ;
      checkArgument( ! Strings.isNullOrEmpty( executableJavaClassName ) ) ;
      this.executableJavaClassName = executableJavaClassName ;
      this.programArguments = checkNotNull( programArguments ) ;
    }

  }

  public SshJavaDriver( final Logger logger, final SshService sshService ) {
    super( logger, sshService ) ;
  }

  /**
   * A value of 16 causes some crash in SSH session creation.
   */
  private static final int MAXIMUM_SCP_SESSIONS = 8 ;

  @Override
  protected String createCommandLine() throws Exception {
    final StringBuilder commandLineBuilder = new StringBuilder() ;
    appendJavaClassLaunch( commandLineBuilder ) ;
    commandLineBuilder.append( Joiner.on( ' ' ).join( setup().programArguments ) ) ;
    return commandLineBuilder.toString() ;
  }

  protected final void appendJavaClassLaunch(
      final StringBuilder commandLineBuilder
  ) throws IOException {
    commandLineBuilder.append( setup().pathToJavaVirtualMachine ) ;
    addSystemProperties( commandLineBuilder ) ;
    addUnauthenticatedJmx( commandLineBuilder ) ;
    addClasspath( commandLineBuilder ) ;
    commandLineBuilder.append( ' ' ) ;
    commandLineBuilder.append( setup().executableJavaClassName ) ;
    commandLineBuilder.append( ' ' ) ;
  }

  @Override
  protected void customInitialize() throws Exception {
    if( bareExecutionContext().firstStart ) {
      synchronizeFiles() ;
    } else {
      logger.info( "Past first start, skipping file synchronization." ) ;
    }
    super.customInitialize() ;
  }

  protected final void synchronizeFiles()
      throws IOException, ExecutionException, InterruptedException
  {
    final ExecutorService executorService =
        Executors.newFixedThreadPool( MAXIMUM_SCP_SESSIONS, threadFactory( "scp" ) ) ;

    final Synchronizer synchronizer = new Synchronizer(
        sshService,
        setup().localFileBundle.get().fileDetails,
        setup().remoteFileEnumerator,
        executorService
    ) ;

    synchronizer.synchronize() ;
  }

  protected final void addSystemProperties( final StringBuilder commandLineBuilder ) {
    if( ! setup().systemProperties.isEmpty() ) {
      commandLineBuilder
          .append( ' ' )
          .append( Joiner.on( ' ' ).join( setup().systemProperties ) ) ;
    }
  }

  protected final void addUnauthenticatedJmx( final StringBuilder commandLineBuilder ) {
    if( setup().jmxPort != null ) {
      commandLineBuilder.append( " -Dcom.sun.management.jmxremote.port=" )
          .append( setup().jmxPort ) ;
      commandLineBuilder.append( " -Dcom.sun.management.jmxremote.ssl=false" ) ;
      commandLineBuilder.append( " -Dcom.sun.management.jmxremote.authenticate=false" ) ;
    }
  }

  protected final void addClasspath( final StringBuilder commandLineBuilder ) throws IOException {
    commandLineBuilder
        .append( " -cp " )
        .append( setup().remoteBaseDirectory )
        .append( '/' )
        .append( setup().remoteFileEnumerator.parentDirectoryByKind.get( FileKind.PACKAGED ) )
        .append( "/*" )
    ;
    for( final String relativeRoot : setup().localFileBundle.get().classpathRelativeRoots ) {
      commandLineBuilder
          .append( ':' )
          .append( setup().remoteBaseDirectory )
          .append( '/' )
          .append( setup().remoteFileEnumerator.parentDirectoryByKind.get( FileKind.COMPILED ) )
          .append( '/' )
          .append( relativeRoot )
      ;
    }
    commandLineBuilder.append( ' ' ) ;
  }

}
