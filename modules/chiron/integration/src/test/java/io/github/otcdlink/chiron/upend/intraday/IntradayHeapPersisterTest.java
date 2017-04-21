package io.github.otcdlink.chiron.upend.intraday;

import com.google.common.base.Charsets;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.integration.echo.EchoUpwardDuty;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IntradayHeapPersisterTest extends AbstractIntradayPersisterTest {

// =======
// Fixture
// =======

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayHeapPersisterTest.class ) ;

  @Override
  protected PersisterKit newPersisterKit() {
    return new PrivatePersisterKit( 1000, LineBreak.CR_UNIX ) ;
  }

  private final class PrivatePersisterKit extends PersisterKit {
    public PrivatePersisterKit(
        final int bufferSize,
        final LineBreak lineBreak
    ) {
      super( bufferSize, lineBreak ) ;
    }

    @Override
    public String loadActualFile() throws IOException {
      final byte[] bytes = ( ( IntradayHeapPersister ) persister ).getBytes() ;
      return new String( bytes, Charsets.US_ASCII ) ;
    }

    @Override
    protected IntradayPersister< Designator, EchoUpwardDuty< Designator > > createPersister() {
      return new IntradayHeapPersister<>(
          bufferSize,
          new FileDesignatorCodecTools.InwardDesignatorEncoder(),
          1,
          "JustTesting",
          lineBreak
      ) ;

    }
  }

}