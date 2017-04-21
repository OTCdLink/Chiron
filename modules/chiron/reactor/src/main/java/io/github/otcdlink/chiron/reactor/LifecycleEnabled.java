package io.github.otcdlink.chiron.reactor;

import java.util.concurrent.TimeUnit;

public interface LifecycleEnabled {
  void start() throws Exception ;
  void stop( long timeout, TimeUnit timeUnit ) throws Exception ;
}
