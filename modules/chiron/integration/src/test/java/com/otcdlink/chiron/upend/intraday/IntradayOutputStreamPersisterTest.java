package com.otcdlink.chiron.upend.intraday;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class IntradayOutputStreamPersisterTest extends AbstractIntradayPersisterTest {

// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayOutputStreamPersisterTest.class ) ;

  @Override
  protected PersisterKit newPersisterKit() {
    return new PrivatePersisterKit() ;
  }

  private static final ThreadLocal< File > CAPTURE_FILE = new ThreadLocal<>() ;

  private static int captureFile( final File file, final int any ) {
    CAPTURE_FILE.set( file ) ;
    return any ;
  }

  private final class PrivatePersisterKit extends PersisterKit {

    public final File intradayFile ;


    public PrivatePersisterKit() {
      this(
          methodSupport.getDirectory(),
          BUFFER_SIZE,
          IntradayPersistenceConstants.LINE_BREAK
      ) ;
    }

    public PrivatePersisterKit(
        final File directory,
        final int bufferSize,
        final LineBreak lineBreak
    ) {
      super(
          captureFile( new File( directory, INTRADAY_RELATIVE_FILENAME ), bufferSize ),
          lineBreak
      ) ;
      this.intradayFile = CAPTURE_FILE.get() ;
      CAPTURE_FILE.set( null ) ;
    }

    @Override
    public String loadActualFile() throws IOException {
      return Files.toString( intradayFile, Charsets.US_ASCII ) ;
    }

    @Override
    protected IntradayPersister< Designator, EchoUpwardDuty< Designator >> createPersister() {
      final File file = CAPTURE_FILE.get() ;
      return new IntradayOutputStreamPersister<>(
          file,
          bufferSize,
          new FileDesignatorCodecTools.InwardDesignatorEncoder(),
          1,
          "JustTesting",
          lineBreak
      ) ;

    }
  }

}