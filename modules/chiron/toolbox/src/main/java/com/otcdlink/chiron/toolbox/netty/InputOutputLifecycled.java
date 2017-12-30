package com.otcdlink.chiron.toolbox.netty;

import com.otcdlink.chiron.toolbox.service.Lifecycled;

import java.io.Closeable;
import java.io.IOException;

public interface InputOutputLifecycled extends Closeable, Lifecycled< Void > {

  /**
   * Asynchronous (faster), only for {@code Closeable} support in tests within
   * a {@code try( ... )} clause.
   * Prefer {@link #stop()}{@code .join()} for explicit calls.
   */
  @Override
  default void close() throws IOException {
    stop() ;
  }


}
