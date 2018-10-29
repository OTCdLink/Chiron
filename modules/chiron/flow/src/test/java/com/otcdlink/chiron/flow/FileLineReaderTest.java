package com.otcdlink.chiron.flow;

import com.otcdlink.chiron.testing.junit5.DirectoryExtension;
import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import com.otcdlink.chiron.toolbox.text.Plural;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

class FileLineReaderTest {

  /**
   * This test is more about investigating {@link Flux} behavior than testing {@link FileLineReader}
   * itself.
   */
  @Test
  void readFile() throws Exception {
    final File file = directoryExtension.newFile( "lines.txt" ) ;
    writeFile( file, 100 ) ;
    final Semaphore completion = new Semaphore( 0 ) ;

    Flux
        .fromIterable( new FileLineReader( file ).linesAsIterable() )
        .publishOn( scheduler( "file" ) )
        .doOnNext( line -> LOGGER.info( "Hook: can see '" + line + "'." ) )
        .publishOn( scheduler( "print" ) )
        .doOnComplete( () -> {
          LOGGER.info( "Hook: complete." ) ;
          completion.release() ;
        } )
        .subscribe( line -> LOGGER.info( "Got '" + line + "'." ) )
    ;

    completion.acquire() ;
  }


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( FileLineReaderTest.class ) ;


  @SuppressWarnings( "WeakerAccess" )
  @RegisterExtension
  final DirectoryExtension directoryExtension = new DirectoryExtension() ;


  private void writeFile( final File file, final int lineCount ) throws IOException {
    try( final FileWriter fileWriter = new FileWriter( file ) ;
         final BufferedWriter bufferedWriter = new BufferedWriter( fileWriter )
    ) {
      for( int i = 0 ; i < lineCount ; i ++ ) {
        bufferedWriter.write( "line " + i + "\n" ) ;
      }
    }
    LOGGER.info(
        "Wrote " + Plural.s( lineCount, "line" ) + " into " + file.getAbsolutePath() + "." ) ;
  }

  private static Scheduler scheduler( final String threadpoolRadix ) {
    return Schedulers.fromExecutor(
        ExecutorTools.singleThreadedExecutorServiceFactory( threadpoolRadix ).create() ) ;
  }


}
