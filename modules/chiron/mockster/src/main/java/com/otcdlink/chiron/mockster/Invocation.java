package com.otcdlink.chiron.mockster;

import com.google.common.collect.Lists;
import com.otcdlink.chiron.toolbox.ToStringTools;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

abstract class Invocation {
  public final int invocationIndex ;
  public final Object target ;
  public final Method method ;

  /**
   * Contains the arguments themselves for an {@link OperativeInvocation}, or a list of
   * {@link ArgumentTrap}s for a {@link VerifyingInvocation}.
   */
  public final List< Object > arguments ;

  public final Object result ;
  public final Throwable throwable ;

  public Invocation(
      final int invocationIndex,
      final Object target,
      final Method method,
      final List< Object > arguments,
      final Object result,
      final Throwable throwable
  ) {
    this.invocationIndex = invocationIndex ;
    this.target = checkNotNull( target ) ;
    this.method = checkNotNull( method ) ;
    this.arguments = Collections.unmodifiableList( Lists.newArrayList( arguments ) ) ;
    if( throwable == null ) {
      this.result = result ;
      this.throwable = null ;
    } else {
      checkArgument( result == null, "Should be null but is " + result ) ;
      this.result = null ;
      this.throwable = throwable ;
    }
  }

  @Override
  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder() ;
    stringBuilder
        .append( ToStringTools.getNiceClassName( this ) ).append( "{" )
        .append( "invocationIndex=" ).append( invocationIndex ).append( ';' )
        .append( "target=" ).append( VerifierTools.proxyAwareToString( target ) ).append( ';' )
        .append( "method=" ).append( method ).append( ';' )
        .append( "arguments=" )
            .append( arguments == null ? "null" : arguments.toString() ).append( ';' )

    ;
    if( ! Void.class.equals( method.getReturnType() ) ) {
      stringBuilder.append( "result=" ).append( result ).append( ';' ) ;
    }
    if( throwable != null ) {
      stringBuilder.append( "throwable=" ).append( result ).append( ';' ) ;
    }
    enrichToString( stringBuilder ) ;
    return stringBuilder.append( "}" ).toString() ;
  }

  protected void enrichToString( final StringBuilder stringBuilder ) { }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final Invocation that = ( Invocation ) other ;
    return invocationIndex == that.invocationIndex &&
        Objects.equals( target, that.target ) &&
        Objects.equals( method, that.method ) &&
        Objects.equals( arguments, that.arguments ) &&
        Objects.equals( result, that.result ) &&
        Objects.equals( throwable, that.throwable )
    ;
  }

  @Override
  public int hashCode() {
    return Objects.hash( invocationIndex, target, method, arguments, result, throwable ) ;
  }
}
