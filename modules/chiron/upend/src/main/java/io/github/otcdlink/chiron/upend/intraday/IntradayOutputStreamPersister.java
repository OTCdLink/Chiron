package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.command.codec.Encoder;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation using {@link FileChannel}.
 */
public final class IntradayOutputStreamPersister< DESIGNATOR, DUTY >
    extends AbstractIntradayPersister< DESIGNATOR, DUTY, OutputStream >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayOutputStreamPersister.class ) ;

  private final File intradayFile ;

  public IntradayOutputStreamPersister(
      final File intradayFile,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, false ) ;
    this.intradayFile = checkNotNull( intradayFile ) ;
  }

  public IntradayOutputStreamPersister(
      final File intradayFile,
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
        false
    ) ;
    this.intradayFile = checkNotNull( intradayFile ) ;
  }

  @Override
  protected String toStringBody() {
    return intradayFile.getAbsolutePath() ;
  }

  @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
  @Override
  protected OutputStream createSink() throws IOException {
    ensureFileExists( this.intradayFile ) ;
    return new FileOutputStream( this.intradayFile ) ;
  }

  @Override
  protected void writeByteBufferToSink(
      final OutputStream sink,
      final ByteBuffer lineByteBuffer,
      final long writtenBytes
  ) throws IOException {
    final byte[] array = lineByteBuffer.array() ;
    sink.write( array, 0, lineByteBuffer.limit() ) ;
  }

  @Override
  protected void flush( final OutputStream sink ) throws IOException {
    sink.flush() ;
  }
}
