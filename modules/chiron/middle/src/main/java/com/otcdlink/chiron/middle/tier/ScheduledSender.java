package com.otcdlink.chiron.middle.tier;

import com.otcdlink.chiron.toolbox.ToStringTools;
import org.joda.time.Duration;
import org.slf4j.Logger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface ScheduledSender< OBJECT > {

  ScheduledFuture< ? > scheduledSend( OBJECT message, Duration delay ) ;

  static< OBJECT > ScheduledSender< OBJECT > wrap(
      final Consumer< OBJECT > consumer,
      final ScheduledExecutorService scheduledExecutorService,
      final Logger logger
  ) {
    return new ScheduledSender< OBJECT >() {
      @Override
      public ScheduledFuture< ? > scheduledSend( final OBJECT message, final Duration delay ) {
        final ScheduledFuture< ? > scheduledFuture = scheduledExecutorService.schedule(
            () -> consumer.accept( message ),
            delay.getMillis(),
            TimeUnit.MILLISECONDS
        ) ;
        logger.debug( "Scheduled consumption of " + message + " into " + consumer +
            " in " + delay.getMillis() + " ms." ) ;
        return scheduledFuture ;
      }

      @Override
      public String toString() {
        return ToStringTools.nameAndCompactHash( this ) + "{" +
            consumer + ";" + scheduledExecutorService + "}" ;
      }
    } ;

  }

}
