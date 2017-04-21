package io.github.otcdlink.chiron.fixture;

import io.github.otcdlink.chiron.testing.MethodSupport;

public class TestNameTools {

  /**
   * Sadly the {@link MethodSupport} causes the test method to run in a silly-named tread
   * ("Thread-0" usually) and there is no option to rename it.
   * So we need to call this method at the start of each test method.
   *
   * TODO: check if we can do this in {@link MethodSupport}.
   */
  public static void setTestThreadName() {
    Thread.currentThread().setName(
        "test-" + new Exception().getStackTrace()[ 1 ].getMethodName() ) ;
  }
}
