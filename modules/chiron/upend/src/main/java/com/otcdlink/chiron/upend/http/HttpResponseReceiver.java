package com.otcdlink.chiron.upend.http;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.buffer.BytebufCoat;
import com.otcdlink.chiron.command.Command;
import io.netty.channel.ChannelPipeline;

import java.io.Writer;

/**
 * The object to pass to {@link Command#callReceiver(Object)} to not expose a
 * {@link ChannelPipeline} at {@link Command} level.
 * The choice of a {@code Writer} enables compatibility with {@code AbstractXhtmlRenderer}.
 */
public interface HttpResponseReceiver {

  /**
   * Must be called first.
   */
  void status( int code ) ;

  /**
   * Optional, if called it must be called immediately after {@link #status(int)}.
   */
  void setHeaders( ImmutableMultimap< String, String > headers ) ;

  /**
   * Don't keep a reference on the {@code Writer} because it might be recycled after
   * {@link Command#callReceiver(Object)} execution since implementation can work as a
   * {@link BytebufCoat}.
   */
  Writer writer() ;

}
