package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.command.codec.Encoder;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

/**
 * Test-only implementation using {@link java.io.ByteArrayOutputStream}.
 */
public final class IntradayHeapPersister< DESIGNATOR, DUTY >
    extends AbstractIntradayPersister< DESIGNATOR, DUTY, ByteArrayOutputStream >
{

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayHeapPersister.class ) ;

  public IntradayHeapPersister(
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, true ) ;
  }

  public IntradayHeapPersister(
      final int lineBufferSize,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion,
      final LineBreak lineBreak
  ) {
    super(
        lineBufferSize,
        designatorEncoder,
        schemaVersion,
        applicationVersion,
        lineBreak,
        true
    ) ;
  }

  @Override
  protected String toStringBody() {
    return ByteArrayOutputStream.class.getSimpleName() ;
  }

  @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
  @Override
  protected ByteArrayOutputStream createSink() throws FileNotFoundException {
    return new ByteArrayOutputStream() ;
  }

  @Override
  protected void writeByteBufferToSink(
      final ByteArrayOutputStream sink,
      final ByteBuffer lineByteBuffer,
      final long writtenBytes
  ) throws IOException {
    while( lineByteBuffer.hasRemaining() ) {
      sink.write( lineByteBuffer.get() ) ;
    }
  }

  private byte[] lastBytes = null ;

  @Override
  protected void flush( final ByteArrayOutputStream sink ) throws IOException {
    lastBytes = sink.toByteArray() ;
  }

  public final byte[] getBytes() {
    checkState( lastBytes != null ) ;
    return lastBytes ;
  }
}
