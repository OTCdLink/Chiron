package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.codec.Encoder;
import com.otcdlink.chiron.toolbox.text.LineBreak;
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
public final class JournalHeapPersister< DESIGNATOR, DUTY >
    extends AbstractJournalPersister< DESIGNATOR, DUTY, ByteArrayOutputStream >
{

  @SuppressWarnings( "unused" )
  private static final Logger LOGGER = LoggerFactory.getLogger( JournalHeapPersister.class ) ;

  public JournalHeapPersister(
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, true ) ;
  }

  public JournalHeapPersister(
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

  @Override
  public PersisterWhenFrozen whenFrozen() {
    return new InnerLockablePersister() ;
  }

  public final byte[] getBytes() {
    checkState( lastBytes != null ) ;
    return lastBytes ;
  }
}
