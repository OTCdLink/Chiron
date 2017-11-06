package com.otcdlink.chiron.middle.tier;

import com.google.common.collect.ImmutableMultimap;
import com.otcdlink.chiron.toolbox.diagnostic.BaseDiagnostic;

public class TimeBoundaryDiagnostic extends BaseDiagnostic {

  public TimeBoundaryDiagnostic(
      final TimeBoundary.ForAll timeBoundary
  ) {
    super( properties( timeBoundary ) ) ;
  }

  private static ImmutableMultimap< String, String > properties(
      final TimeBoundary.ForAll timeBoundary
  ) {
    final ImmutableMultimap.Builder< String, String > builder = ImmutableMultimap.builder() ;
    if( timeBoundary == null ) {
      builder.put( NO_KEY, "[No " + TimeBoundary.ForAll.class.getSimpleName() + " defined yet]" ) ;
    } else {
      builder.put( "Ping interval", timeBoundary.pingIntervalMs + " ms" ) ;
      builder.put( "Ping timeout", timeBoundary.pingTimeoutMs + " ms" ) ;
      builder.put( "Pong timeout", timeBoundary.pongTimeoutMs + " ms" ) ;
      builder.put( "Maximum session inactivity",
          timeBoundary.sessionInactivityMaximumMs + " ms" ) ;
      builder.put(
          "Reconnect delay range",
          "[ " +
              timeBoundary.reconnectDelayRangeMs.lowerBound + ".." +
              timeBoundary.reconnectDelayRangeMs.upperBound + " ] ms"
      ) ;
    }
    return builder.build() ;
  }
}
