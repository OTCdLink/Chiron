package io.github.otcdlink.chiron.command.codec;

import io.github.otcdlink.chiron.buffer.PositionalFieldReader;

import java.io.IOException;

public interface Decoder< OBJECT > {
  OBJECT decodeFrom( PositionalFieldReader positionalFieldReader ) throws IOException ;
}
