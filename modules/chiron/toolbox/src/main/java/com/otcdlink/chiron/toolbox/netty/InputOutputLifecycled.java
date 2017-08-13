package com.otcdlink.chiron.toolbox.netty;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface InputOutputLifecycled extends Closeable {

  CompletableFuture< ? > start() ;

  CompletableFuture< ? > stop() ;

  /**
   * Asynchronous (faster), only for {@code Closeable} support in tests within
   * a {@code try( ... )} clause.
   * Prefer {@link #stop()} for explicit calls.
   */
  @Override
  default void close() throws IOException {
    stop() ;
  }


}
