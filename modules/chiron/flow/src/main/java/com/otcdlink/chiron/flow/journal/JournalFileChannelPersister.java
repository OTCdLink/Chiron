package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.command.codec.Encoder;
import com.otcdlink.chiron.toolbox.text.LineBreak;
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
public final class JournalFileChannelPersister< DESIGNATOR, DUTY >
    extends AbstractJournalPersister< DESIGNATOR, DUTY, FileChannel >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( JournalFileChannelPersister.class ) ;

  private final File journalFile ;

  public JournalFileChannelPersister(
      final File journalFile,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion
  ) {
    super( designatorEncoder, schemaVersion, applicationVersion, true ) ;
    this.journalFile = checkNotNull( journalFile ) ;
  }

  public JournalFileChannelPersister(
      final File journalFile,
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
    this.journalFile = checkNotNull( journalFile ) ;
  }

  @Override
  protected String toStringBody() {
    return journalFile.getAbsolutePath() ;
  }

  @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
  @Override
  protected FileChannel createSink() throws IOException {
    ensureFileExists( this.journalFile ) ;
    return new RandomAccessFile( this.journalFile, "rw" ).getChannel() ;
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
    // Don't do anything, it's a memory-mapped file, let the OS decide when to flush.
    // sink.force( false ) ;
  }

  @Override
  public PersisterWhenFrozen whenFrozen() {
    return new InnerLockablePersister() ;
  }
}
