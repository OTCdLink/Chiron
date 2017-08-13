package com.otcdlink.chiron.command.codec;

import com.otcdlink.chiron.buffer.PositionalFieldWriter;

import java.io.IOException;

public interface Encoder< OBJECT > {
  void encodeTo( final OBJECT object, PositionalFieldWriter positionalFieldWriter ) throws IOException ;
}
