package io.github.otcdlink.chiron.middle.throttler;

import org.joda.time.Duration;

public interface ThrottlingDurationAcceptor {

  void throttlingDuration( final Duration duration ) ;
}
