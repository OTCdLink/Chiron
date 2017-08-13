package com.otcdlink.chiron.middle.tier;

import com.otcdlink.chiron.toolbox.diagnostic.AbstractDiagnostic;

import java.io.IOException;
import java.io.Writer;

public class TimeBoundaryDiagnostic extends AbstractDiagnostic {

  private final TimeBoundary.ForAll timeBoundary ;

  public TimeBoundaryDiagnostic(
      final int depth,
      final String indent,
      final TimeBoundary.ForAll timeBoundary
  ) {
    super( depth, indent ) ;
    this.timeBoundary = timeBoundary ;
  }

  @Override
  protected void printSelf( final Writer writer ) throws IOException {
    if( timeBoundary == null ) {
      printBodyLine( writer, "[No " + TimeBoundary.ForAll.class.getSimpleName() + " defined yet]" ) ;
    } else {
      printBodyLine( writer, "Ping interval = " + timeBoundary.pingIntervalMs + " ms" ) ;
      printBodyLine( writer, "Ping timeout = " + timeBoundary.pingTimeoutMs + " ms" ) ;
      printBodyLine( writer, "Pong timeout = " + timeBoundary.pongTimeoutMs + " ms" ) ;
      printBodyLine( writer, "Maximum session inactivity = " +
          timeBoundary.sessionInactivityMaximumMs + " ms" ) ;
      printBodyLine(
          writer,
          "Reconnect delay range = [ " +
          timeBoundary.reconnectDelayRangeMs.lowerBound + ".." +
          timeBoundary.reconnectDelayRangeMs.upperBound + " ] ms"
      ) ;
    }
  }
}
