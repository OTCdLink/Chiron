package com.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.AbstractInvocationHandler;
import com.otcdlink.chiron.toolbox.ToStringTools;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public final class MethodCallRecorder< DUTY > {

  private final MethodCaller< DUTY > methodCaller ;
  private final DUTY dutyProxy ;
  private final Consumer< MethodCall > methodCallConsumer ;

  public MethodCallRecorder(
      final MethodCaller< DUTY > methodCaller,
      final Consumer< MethodCall > methodCallConsumer
  ) {
    this.methodCaller = checkNotNull( methodCaller ) ;
    final Class[] interfaces = { methodCaller.targetInterface() } ;
    dutyProxy = newDutyProxy( interfaces ) ;
    this.methodCallConsumer = checkNotNull( methodCallConsumer ) ;
  }

  public DUTY recordingDuty() {
    return dutyProxy ;
  }

  @SuppressWarnings( "unchecked" )
  private DUTY newDutyProxy( final Class[] interfaces ) {
    return ( DUTY ) Proxy.newProxyInstance(
        MethodCallRecorder.class.getClassLoader(),
        interfaces,
        new AbstractInvocationHandler() {
          @Override
          protected Object handleInvocation(
              final Object proxy,
              final Method method,
              final Object[] arguments
          ) throws Throwable {
            methodCallConsumer.accept( new MethodCall( method, arguments ) ) ;
            return null ;
          }

          @Override
          public String toString() {
            return MethodCallRecorder.class.getSimpleName() + "$recorder@" +
                ToStringTools.compactHashForNonNull( this ) ;
          }
        }
    ) ;
  }

  public void callMethods() {
    methodCaller.callMethods( recordingDuty() ) ;
  }

  public static < DUTY > ImmutableList< MethodCall > recordMethodCalls(
      final MethodCaller< DUTY > methodCaller
  ) {
    final ImmutableList.Builder< MethodCall > recorder = ImmutableList.builder() ;
    new MethodCallRecorder<>( methodCaller, recorder::add ).callMethods() ;
    return recorder.build() ;
  }

}
