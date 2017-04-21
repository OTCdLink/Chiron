package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.command.codec.Encoder;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation using {@link FileChannel}.
 */
public final class IntradayFileChannelPersister< DESIGNATOR, DUTY >
    extends AbstractIntradayPersister< DESIGNATOR, DUTY, FileChannel >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayFileChannelPersister.class ) ;

  private final File intradayFile ;

  public IntradayFileChannelPersister(
      final File intradayFile,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, true ) ;
    this.intradayFile = checkNotNull( intradayFile ) ;
  }

  public IntradayFileChannelPersister(
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
        true
    ) ;
    this.intradayFile = checkNotNull( intradayFile ) ;
  }

  @Override
  protected String toStringBody() {
    return intradayFile.getAbsolutePath() ;
  }

  @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
  @Override
  protected FileChannel createSink() throws IOException {
    ensureFileExists( this.intradayFile ) ;
    return new RandomAccessFile( this.intradayFile, "rw" ).getChannel() ;
  }

  @Override
  protected void writeByteBufferToSink(
      final FileChannel sink,
      final ByteBuffer lineByteBuffer,
      final long writtenBytes
  ) throws IOException {
    sink.write( lineByteBuffer, writtenBytes ) ;
  }

  @Override
  protected void flush( final FileChannel sink ) throws IOException {
    sink.force( false ) ;
  }
}
