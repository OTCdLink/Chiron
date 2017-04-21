package io.github.otcdlink.chiron.fixture.httpproxy;

import com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public interface AsciiStringReader {
  String read() throws IOException ;

  class Queuing implements AsciiStringReader {

    private final BlockingQueue< String > queue = new LinkedBlockingQueue<>() ;

    public void feed( final String string ) {
      Uninterruptibles.putUninterruptibly( queue, string ) ;
    }

    @Override
    public String read() throws IOException {
      return Uninterruptibles.takeUninterruptibly( queue ) ;
    }
  }
}
