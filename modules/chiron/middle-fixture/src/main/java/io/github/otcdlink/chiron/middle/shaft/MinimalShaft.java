package io.github.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;

/**
 * Just verifies that method calls happened.
 */
public class MinimalShaft< DUTY > implements MethodCallShaft< DUTY > {

  @Override
  public void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier
  ) {
    final ImmutableList< MethodCall > methodCallRecording1 =
        MethodCallRecorder.recordMethodCalls( methodCaller ) ;
    final ImmutableList< MethodCall > methodCallRecording2 =
        MethodCallRecorder.recordMethodCalls( methodCaller ) ;

    MethodCallVerifier.verifyAll( methodCallRecording1, methodCallRecording2, methodCallVerifier ) ;
  }

}
