package io.github.otcdlink.chiron.command.codec;

import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;

import java.io.IOException;

public interface Encoder< OBJECT > {
  void encodeTo( final OBJECT object, PositionalFieldWriter positionalFieldWriter ) throws IOException ;
}
