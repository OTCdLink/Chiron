package com.otcdlink.chiron.codec;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.middle.session.SessionIdentifier;

import java.io.IOException;

/**
 * Creates a {@link Command} given the {@link ENDPOINT_SPECIFIC} part and the {@link Command}
 * {@link Command.Description#name()}. The {@link CommandBodyDecoder} is useful for reading
 * a {@link Command} "from the wire" with the {@link SessionIdentifier} known from inside Netty's
 * Pipeline.
 * Implicitely, this interface requires the Upend implementor to decide of
 * {@link ENDPOINT_SPECIFIC}'s class.
 */
public interface CommandBodyDecoder<
    ENDPOINT_SPECIFIC,
    CALLABLE_RECEIVER
> {
  /**
   * @return {@code null} if no match found.
   * @throws DecodeException if something went wrong.
   */
  Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > decodeBody(
      ENDPOINT_SPECIFIC endpointSpecific,
      String commandName,
      PositionalFieldReader positionalFieldReader
  ) throws IOException;

}
