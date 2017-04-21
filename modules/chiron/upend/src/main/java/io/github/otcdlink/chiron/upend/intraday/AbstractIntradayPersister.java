package io.github.otcdlink.chiron.upend.intraday;

import io.github.otcdlink.chiron.buffer.BytebufTools;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.codec.Encoder;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Persists {@link Command}s somewhere.
 * One single line (corresponding to one {@link Command} should fit in the {@link ByteBuffer}.
 *
 * TODO: support a {@link ByteBuf} passed through the {@link DESIGNATOR} by an
 * {@link IntradayFileReplayer} if {@link #schemaVersion}s are compatible.
 */
public abstract class AbstractIntradayPersister< DESIGNATOR, DUTY, SINK extends Closeable >
    implements IntradayPersister< DESIGNATOR, DUTY >
{

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractIntradayPersister.class ) ;

  private static final int DEFAULT_WRITE_BUFFER_SIZE = 1024 * 1024 ;

  private final Encoder< DESIGNATOR > designatorEncoder ;
  private final int schemaVersion ;
  private final String applicationVersion ;
  private final String lineBreak ;

  private final ByteBuffer lineByteBuffer;
  private final ByteBuf lineByteBuf ;
  private final PositionalFieldWriter fieldWriter ;

  protected AbstractIntradayPersister(
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion,
      final boolean directBuffer
  ) {
    this(
        DEFAULT_WRITE_BUFFER_SIZE,
        designatorEncoder,
        schemaVersion,
        applicationVersion,
        IntradayPersistenceConstants.LINE_BREAK,
        directBuffer
    ) ;
  }

  protected AbstractIntradayPersister(
      final int lineBufferSize,
      final Encoder< DESIGNATOR > designatorEncoder,
      final int schemaVersion,
      final String applicationVersion,
      final LineBreak lineBreak,
      final boolean directBuffer
  ) {
    checkArgument( lineBufferSize > 0 ) ;
    this.designatorEncoder = checkNotNull( designatorEncoder ) ;
    this.schemaVersion = checkNotNull( schemaVersion ) ;
    this.applicationVersion = checkNotNull( applicationVersion ) ;
    this.lineBreak = lineBreak.asString ;

    if( directBuffer ) {
      this.lineByteBuffer = ByteBuffer.allocateDirect( lineBufferSize ) ;
    } else {
      this.lineByteBuffer = ByteBuffer.allocate( lineBufferSize ) ;
    }
    this.lineByteBuf = Unpooled.wrappedBuffer( lineByteBuffer ) ;
    this.fieldWriter = BytebufTools.coat( lineByteBuf ) ;
  }

  @Override
  public final String toString() {
    return ToStringTools.nameAndCompactHash( this ) + '{' + toStringBody() + '}' ;
  }

  protected String toStringBody() {
    return "" ;
  }

  private SINK sink = null ;
  private long writtenBytes ;

  @SuppressWarnings( "IOResourceOpenedButNotSafelyClosed" )
  @Override
  public void open() throws IOException {
    checkState( sink == null, "Already open" ) ;
    sink = createSink() ;
    writtenBytes = 0 ;
    prepareWrite() ;
    fieldWriter.writeAsciiUnsafe( IntradayPersistenceConstants.MAGIC ) ;
    fieldWriter.writeAsciiUnsafe( " " + schemaVersion + " " ) ;
    fieldWriter.writeAsciiUnsafe( "ApplicationVersion " ) ;
    fieldWriter.writeAsciiUnsafe( applicationVersion ) ;
    fieldWriter.writeAsciiUnsafe( lineBreak ) ;
    completeWrite() ;
    flush( sink ) ;
    LOGGER.info( "Opened " + this + "." ) ;
  }

  protected abstract SINK createSink() throws IOException;

  /**
   * Mostly copied from Jakarta Commons IO 2.4,
   * {@code org.apache.commons.io.FileUtils#openOutputStream(java.io.File, boolean)}
   */
  static void ensureFileExists( final File file ) throws IOException {
    if( file.exists() ) {
      if( file.isDirectory() ) {
        throw new IOException( "File '" + file + "' exists but is a directory" ) ;
      }
      if( !file.canWrite() ) {
        throw new IOException( "File '" + file + "' cannot be written to" ) ;
      }
    } else {
      final File parent = file.getParentFile() ;
      if( parent != null ) {
        if( ! parent.mkdirs() && !parent.isDirectory() ) {
          throw new IOException( "Directory '" + parent + "' could not be created" ) ;
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    checkOpen() ;
    try {
      flush( sink ) ;
      sink.close() ;
      LOGGER.info( "Closed " + this + "." ) ;
    } finally {
      sink = null ;
    }
  }

  private boolean autoFlush = false ;

  /**
   * A test with A MacBook Pro Retina 2013, 2,7 GHz Intel Core i7, 16 GB RAM 1600 MHz DDR3, SSD,
   * shows 8k {@link Command}/s with {@link #autoFlush},
   * and 180k {@link Command}/s without it.
   * 180k {@link Command}/s means 20 s to rewrite a 1-hour Intraday file
   * with 1000 {@link Command}/s, so replaying a day of 8 hours would take 160 s.
   * Real-world figures will differ, because of {@link Command} size, rotating disk, number of
   * cores, and processor frequency.
   */
  @Override
  public void autoFlush( final boolean flushEachLine ) {
    this.autoFlush = flushEachLine ;
  }

  @Override
  public void accept( final Command< DESIGNATOR, DUTY > command ) {
    if( command.persist() ) {
      prepareWrite() ;
      try {
        designatorEncoder.encodeTo( command.endpointSpecific, fieldWriter ) ;
        fieldWriter.writeDelimitedString( command.description().name() ) ;
        command.encodeBody( fieldWriter ) ;
        fieldWriter.writeAsciiUnsafe( lineBreak ) ;
        completeWrite() ;
        if( autoFlush ) {
          flush( sink ) ;
        }
      } catch( final IOException e ) {
        LOGGER.error( "Failed to write " + command + "into " + this + ".", e ) ;
      }
    }
  }

  private void checkOpen() {
    checkState( sink != null, "Not open" ) ;
  }

  private void prepareWrite() {
    checkOpen() ;
    lineByteBuf.clear() ;
  }


  private void completeWrite() throws IOException {
    lineByteBuffer.position( 0 ) ;
    lineByteBuffer.limit( lineByteBuf.writerIndex() ) ;
    writeByteBufferToSink( sink, lineByteBuffer, writtenBytes ) ;
    writtenBytes += lineByteBuf.writerIndex() ;
  }

  /**
   *
   * @param lineByteBuffer what to write from, position and limit are set to appropriate values.
   * @param writtenBytes previously written bytes, so a {@code FileChannel} knows the offset.
   */
  protected abstract void writeByteBufferToSink(
      SINK sink,
      ByteBuffer lineByteBuffer,
      long writtenBytes
  ) throws IOException;

  /**
   * Can't rely on {@link java.io.Flushable} because {@link FileChannel} doesn't implement it.
   */
  protected abstract void flush( SINK sink ) throws IOException ;
}
