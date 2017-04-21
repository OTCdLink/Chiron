package io.github.otcdlink.chiron.toolbox.catcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingCatcher implements Catcher {

  private static final Logger LOGGER = LoggerFactory.getLogger( LoggingCatcher.class ) ;
  @Override
  public void processThrowable( final Throwable throwable ) {
    LOGGER.error( "Unanticipated throwable.", throwable ) ;
  }
}
