package io.github.otcdlink.chiron.reactor;

import java.util.concurrent.TimeUnit;

interface CanonicalFlowgraph< COMMAND > {
  void start() throws Exception ;

  default void stop() throws Exception {
    stop( 1, TimeUnit.SECONDS ) ;
  }

  void stop( long timeout, TimeUnit timeUnit ) throws Exception ;

  void injectAtEntry( COMMAND command ) ;

}
