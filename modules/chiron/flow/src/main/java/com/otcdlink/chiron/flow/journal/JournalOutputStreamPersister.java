package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.codec.Encoder;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default implementation using {@link FileOutputStream}.
 */
public final class JournalOutputStreamPersister< DESIGNATOR, DUTY >
    extends AbstractJournalPersister< DESIGNATOR, DUTY, OutputStream >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( JournalOutputStreamPersister.class ) ;

  private final File intradayFile ;

  public JournalOutputStreamPersister(
      final File intradayFile,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, false ) ;
    this.intradayFile = checkNotNull( intradayFile ) ;
  }

  public JournalOutputStreamPersister(
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

  @Override
  public PersisterWhenFrozen whenFrozen() {
    return new InnerLockablePersister() ;
  }
}
