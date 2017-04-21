package io.github.otcdlink.chiron.command.codec;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;

import java.io.IOException;

public interface Codec< OBJECT > extends Encoder< OBJECT >, Decoder< OBJECT > {

  abstract class Nullable< OBJECT > implements Codec< OBJECT > {
    @Override
    public final void encodeTo(
        final OBJECT object,
        final PositionalFieldWriter writer
    ) throws IOException {
      if( ! writer.writeNullityMarkerMaybe( object == null ) ) {
        encodeNonNull( object, writer ) ;
      }
    }

    protected abstract void encodeNonNull( OBJECT object, PositionalFieldWriter writer )
        throws IOException ;

    @Override
    public final OBJECT decodeFrom( final PositionalFieldReader reader ) throws IOException {
      if( reader.readNullityMarker() ) {
        return null ;
      } else {
        return decodeNonNull( reader ) ;
      }
    }

    protected abstract OBJECT decodeNonNull( PositionalFieldReader reader ) throws IOException ;

  }
}
