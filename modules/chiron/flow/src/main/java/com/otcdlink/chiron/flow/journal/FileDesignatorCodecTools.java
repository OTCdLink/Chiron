package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.command.codec.Codec;
import com.otcdlink.chiron.command.codec.Decoder;
import com.otcdlink.chiron.command.codec.Encoder;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.middle.session.SessionIdentifier;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.otcdlink.chiron.designator.Designator.Kind.INTERNAL;

public final class FileDesignatorCodecTools {

  private FileDesignatorCodecTools() { }

  public static final class InwardDesignatorEncoder implements Encoder< Designator > {
    @Override
    public void encodeTo(
        final Designator designator,
        final PositionalFieldWriter positionalFieldWriter
    ) throws IOException {
      final String stringWithRawSeconds = designator.stamp.asStringRoundedToFlooredSecond() ;
      positionalFieldWriter.writeAsciiUnsafe( stringWithRawSeconds ) ;
      positionalFieldWriter.writeAsciiUnsafe( " " ) ;
      positionalFieldWriter.writeNullableString(
          designator.sessionIdentifier == null ? null : designator.sessionIdentifier.asString() ) ;
    }
  }

  /**
   * Not thread-safe because of {@link #stampParser} and {@link #lineNumber}.
   */
  public static final class InwardDesignatorDecoder
      implements
      Decoder< Designator >,
      FileAwareDecoder
  {

    private final Stamp.Parser stampParser = new Stamp.Parser() ;

    @Override
    public Designator decodeFrom( final PositionalFieldReader reader )
        throws IOException
    {
      final String stampAsString = reader.readDelimitedString() ;
      final Stamp stamp = stampParser.parse( stampAsString ) ;
      final String sessionAsString = reader.readNullableString() ;
      if( sessionAsString == null ) {
        return new DesignatorFromFile( INTERNAL, stamp, null, lineNumber ) ;
      } else {
        final SessionIdentifier sessionIdentifier = new SessionIdentifier( sessionAsString ) ;
        return new DesignatorFromFile(
            Designator.Kind.UPWARD, stamp, sessionIdentifier, lineNumber ) ;
      }
    }

    private long lineNumber = -1 ;


    @Override
    public void lineNumber( final long lineNumber ) {
      checkArgument( lineNumber > 0 ) ;
      this.lineNumber = lineNumber ;
    }

  }

  public static class InwardDesignatorCodec implements Codec< Designator > {

    private final InwardDesignatorEncoder encoder = new InwardDesignatorEncoder() ;
    private final InwardDesignatorDecoder decoder = new InwardDesignatorDecoder() ;

    @Override
    public void encodeTo(
        final Designator designatorInward,
        final PositionalFieldWriter writer
    ) throws IOException {
      encoder.encodeTo( designatorInward, writer ) ;
    }

    @Override
    public Designator decodeFrom( final PositionalFieldReader reader )
        throws IOException
    {
      return decoder.decodeFrom( reader ) ;
    }
  }

  public static final class DesignatorFromFile
      extends Designator
      implements Designator.Derivable< DesignatorFromFile >
  {
    public final long lineNumber ;

    private DesignatorFromFile(
        final Kind kind,
        final Stamp stamp,
        final Stamp cause,
        final Command.Tag tag,
        final SessionIdentifier sessionIdentifier,
        final long lineNumber
    ) {
      super( kind, stamp, cause, tag, sessionIdentifier ) ;
      this.lineNumber = lineNumber ;
    }

    public DesignatorFromFile(
        final Kind kind,
        final Stamp stamp,
        final SessionIdentifier sessionIdentifier,
        final long lineNumber
    ) {
      this( kind, stamp, null, null, sessionIdentifier, lineNumber ) ;
    }

    @Override
    protected void addToStringBody( final StringBuilder stringBuilder ) {
      super.addToStringBody( stringBuilder ) ;
      stringBuilder.append( ";line=" ).append( lineNumber ) ;
    }

    @Override
    public DesignatorFromFile derive( final Kind newKind, final Stamp newStamp ) {
      return derive( newKind, newStamp, tag, sessionIdentifier ) ;
    }

    @Override
    public DesignatorFromFile derive(
        final Kind newKind,
        final Stamp newStamp,
        final Command.Tag newTag,
        final SessionIdentifier newSessionIdentifier
    ) {
      return new DesignatorFromFile(
          newKind, newStamp, stamp, newTag, newSessionIdentifier, lineNumber ) ;
    }
  }



  /**
   * Propagate current line number to some {@link DesignatorFromFile}.
   * Implementors becames stateful.
   */
  public interface FileAwareDecoder {
    void lineNumber( final long lineNumber ) ;
  }


}
