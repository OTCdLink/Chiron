package io.github.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.github.otcdlink.chiron.ssh.SshService;
import io.github.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkNotNull;

public class Synchronizer {

  private static final Logger LOGGER = LoggerFactory.getLogger( Synchronizer.class ) ;
  private static final int UPLOADING_THREADS = 8 ;

  private final SshService sshService ;
  private final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles ;
  private final RemoteFileEnumerator remoteFileEnumerator ;
  private final ExecutorService executorService ;

  public Synchronizer(
      final SshService sshService,
      final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles,
      final RemoteFileEnumerator remoteFileEnumerator
  ) {
    this(
        sshService,
        localFiles,
        remoteFileEnumerator,
        Executors.newFixedThreadPool(
            UPLOADING_THREADS,
            new ThreadFactoryBuilder()
                .setNameFormat( Synchronizer.class.getSimpleName() + "-upload-%s" ).build()
        )
    ) ;
  }

  public Synchronizer(
      final SshService sshService,
      final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles,
      final RemoteFileEnumerator remoteFileEnumerator,
      final ExecutorService executorService
  ) {
    this.sshService = checkNotNull( sshService ) ;
    this.localFiles = checkNotNull( localFiles ) ;
    this.remoteFileEnumerator = checkNotNull( remoteFileEnumerator ) ;
    this.executorService = checkNotNull( executorService ) ;
  }

  public void synchronize() throws IOException, ExecutionException, InterruptedException {

    // Nice-to-have: pass localFiles as a Lazy so we can resolve local and remote files in parallel.
    final ImmutableKeyHolderMap< FileKey, FileDetail > remoteFiles =
        remoteFileEnumerator.enumerate( sshService.newSession() ) ;
    LOGGER.info( "Enumerated " + localFiles.size() + " local files." ) ;
    LOGGER.info( "Enumerated " + remoteFiles.size() + " remote files." ) ;

    final FileDelta fileDelta = new FileDelta(
        remoteFileEnumerator.baseDirectory,
        remoteFileEnumerator.parentDirectoryByKind,
        localFiles,
        remoteFiles
    ) ;
    LOGGER.info( "Computed " + fileDelta.remoteDirectoriesToCreate.size() +
        " directories to create." ) ;
    LOGGER.info( "Computed " + fileDelta.localFilesToUpload.size() + " local files to upload." ) ;
    LOGGER.info( "Computed " + fileDelta.remoteFilesToDelete.size() + " remote files to delete." ) ;

    deleteRemoteFiles( fileDelta.remoteFilesToDelete ) ;
    createRemoteDirectories( fileDelta.remoteDirectoriesToCreate ) ;
    uploadFiles( fileDelta.localFilesToUpload, localFiles ) ;
  }

  private void uploadFiles(
      final ImmutableSet< FileKey > localFilesToUpload,
      final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles
  ) throws IOException, InterruptedException, ExecutionException {
    if( localFilesToUpload.isEmpty() ) {
      LOGGER.info( "No local file to upload." ) ;
    } else {
      LOGGER.info( "About to upload " + localFilesToUpload.size() + " files ..." ) ;
      final List< Future< Void > > futures = new ArrayList<>( localFilesToUpload.size() ) ;

      final SCPFileTransfer scpFileTransfer = sshService.newScpFileTransfer() ;
      for( final FileKey fileKey : localFilesToUpload ) {
        final String remoteFilePath = remoteFilePath( fileKey ) ;
        final String localFile = localFiles.get( fileKey ).parentPath + '/' + fileKey.relativePath ;
        futures.add( executorService.submit( () -> {
          final int exitStatus = scpFileTransfer.newSCPUploadClient().copy(
              new FileSystemFile( localFile ),
              remoteFilePath
          ) ;
          final String message = "Uploaded to " + sshService.remoteHost().asString() +
              " local file '" + localFile + "', as '" + remoteFilePath + "', " +
              "exit status: " + exitStatus + "."
          ;
          if( exitStatus == 0 ) {
            LOGGER.debug( message ) ;
          } else {
            LOGGER.warn( message ) ;
          }
          return null ;
        } ) ) ;

      }

      // We don't want to shut down the ExecutorService which may be a shared one.
      for( final Future future : futures ) {
        future.get() ;
      }
      LOGGER.info( "Upload complete." ) ;
    }

  }

  private void deleteRemoteFiles( final ImmutableSet<FileKey> remoteFilesToDelete )
      throws IOException
  {
    if( remoteFilesToDelete.isEmpty() ) {
      LOGGER.info( "No remote file to delete." ) ;
    } else {
      final List< String > files = new ArrayList<>( remoteFilesToDelete.size() ) ;
      for( final FileKey fileKey : remoteFilesToDelete ) {
        final String filePath = remoteFilePath( fileKey ) ;
        files.add( filePath ) ;
      }
      final String allFiles = Joiner.on( ' ' ).join( files ) ;
      final String commandLine = "rm " + allFiles ;
      LOGGER.debug( "Running deletion: " + commandLine ) ;
      final Session.Command command = sshService.newSession().exec( commandLine ) ;
      logOuputLines( "rm", command.getInputStream() ) ;
      command.join() ;
      LOGGER.info( "File deletion terminated with exit status " +
          command.getExitStatus() + "." ) ;
    }

  }

  private String remoteFilePath( final FileKey fileKey ) {
    return remoteFileEnumerator.baseDirectory + '/' +
        remoteFileEnumerator.parentDirectoryByKind.get( fileKey.kind ) + '/' +
        fileKey.relativePath ;
  }

  private void createRemoteDirectories( final ImmutableSet< String > remoteDirectoriesToCreate )
      throws IOException
  {
    final String allDirectories = Joiner.on( ' ' ).join( remoteDirectoriesToCreate ) ;
    final String commandLine = "mkdir --parents " + allDirectories ;
    final Session.Command command = sshService.newSession().exec( commandLine ) ;
    command.join() ;
    LOGGER.info( "Directory creation on " + sshService.remoteHost().asString() +
        " terminated with exit status " + command.getExitStatus() + "." ) ;
  }

  private static void logOuputLines( final String prefix, final InputStream inputStream )
      throws IOException
  {
    try( BufferedReader bufferedReader = new BufferedReader(
        new InputStreamReader( inputStream ) )
    ) {
      while( true ) {
        final String outputLine = bufferedReader.readLine() ;
        if( outputLine == null ) {
          break ;
        }
        LOGGER.debug( prefix + ": " + outputLine ) ;
      }
    }
  }

}
