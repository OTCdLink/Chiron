package com.otcdlink.chiron.middle.throttler;

import com.otcdlink.chiron.command.Command;
import org.joda.time.Duration;

public interface Throttler< COMMAND > {

  void throttlingDuration( Duration throttlingDuration ) ;

  Effect evaluateAndUpdate( COMMAND command ) ;

  enum Effect {
    /**
     * Given {@link Command} doesn't support throttling.
     */
    NOT_APPLICABLE,

    /**
     * Given {@link Command} supports throttling and was throttled.
     */
    THROTTLED,

    /**
     * Given {@link Command} supports throttling and was not throttled.
     */
    PASSED,
    ;
  }


}
