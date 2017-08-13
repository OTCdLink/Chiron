package com.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.api.Assertions;

import java.lang.reflect.Method;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public interface MethodCallVerifier {

  static void verifyAll(
      final ImmutableList< MethodCall > expected,
      final ImmutableList< MethodCall > actual,
      final MethodCallVerifier verifier
  ) {
    Assertions.assertThat( actual ).hasSize( expected.size() ) ;
    for( int i = 0 ; i < expected.size() ; i ++ ) {
      final MethodCall expectedMethodCall = expected.get( i ) ;
      final MethodCall actualMethodCall = actual.get( i ) ;
      verifier.verifyEquivalence( expectedMethodCall, actualMethodCall ) ;
    }
  }

  void verifyEquivalence( MethodCall expected, MethodCall actual ) ;

  class Skipping implements MethodCallVerifier {

    private final ImmutableSet< Integer > skippedParameterIndexes ;

    public Skipping( final int... skippedParameterIndexes ) {
      this( ImmutableSet.copyOf(
          // http://stackoverflow.com/a/27043087/1923328
          IntStream.of( skippedParameterIndexes ).boxed().toArray( Integer[]::new )
      ) ) ;
    }

    public Skipping( final ImmutableSet< Integer > skippedParameterIndexes ) {
      this.skippedParameterIndexes = checkNotNull( skippedParameterIndexes ) ;
    }

    @Override
    public void verifyEquivalence( final MethodCall expected, final MethodCall actual ) {
      checkArgument( expected.parameters.size() == actual.parameters.size() ) ;
      assertThat( actual.method ).isEqualTo( expected.method ) ;
      for( int i = 0 ; i < expected.parameters.size() ; i ++ ) {
        if( ! skippedParameterIndexes.contains( i ) ) {
          final Object actualParameter = actual.parameters.get( i ) ;
          final Object expectedParameter = expected.parameters.get( i ) ;
          final Method method = expected.method ;
          verifyParameter( method, i, expectedParameter, actualParameter ) ;
        }
      }
    }

    protected void verifyParameter(
        final Method method,
        final int parameterIndex,
        final Object expectedParameter, final Object actualParameter
    ) {
      assertThat( actualParameter )
          .describedAs( "Parameter #" + parameterIndex + " in '" + method + "'" )
          .isEqualTo( expectedParameter ) ;
    }
  }

  class Default extends Skipping { }
}
