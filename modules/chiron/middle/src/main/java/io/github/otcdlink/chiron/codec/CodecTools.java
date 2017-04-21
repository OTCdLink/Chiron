package io.github.otcdlink.chiron.codec;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.middle.EnumeratedMessageKind;
import io.github.otcdlink.chiron.middle.TypedNotice;

public final class CodecTools {

  private CodecTools() { }

  /**
   * Decodes a subclass of {@link TypedNotice} from a {@link PositionalFieldReader}.
   */
  public static  <
      NOTICE extends TypedNotice< KIND >,
      KIND extends Enum< KIND > & EnumeratedMessageKind
  > NOTICE decode(
      final TypedNotice.DecodingKit< NOTICE, KIND > decodingKit,
      final PositionalFieldReader reader
  ) throws DecodeException {

    final String ordinalAsString = reader.readDelimitedString() ;
    final String description = reader.readNullableString() ;
    final KIND kind ;
    try {
      final int ordinal = Integer.parseInt( ordinalAsString ) ;
      kind = decodingKit.kind( ordinal ) ;
    } catch( final Exception e ) {
      throw new DecodeException( e.getMessage(), e ) ;
    }
    if( description == null ) {
      return decodingKit.create( kind ) ;
    } else {
      return decodingKit.create( kind, description ) ;
    }
  }
}
