package com.otcdlink.chiron.command.codec;

import com.otcdlink.chiron.buffer.PositionalFieldReader;

import java.io.IOException;

public interface Decoder< OBJECT > {
  OBJECT decodeFrom( PositionalFieldReader positionalFieldReader ) throws IOException ;
}
