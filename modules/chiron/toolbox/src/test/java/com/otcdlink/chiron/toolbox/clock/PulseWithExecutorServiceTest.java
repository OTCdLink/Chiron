package com.otcdlink.chiron.toolbox.clock;

import com.otcdlink.chiron.toolbox.concurrent.ExecutorTools;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;

public class PulseWithExecutorServiceTest extends AbstractPulseTest {


// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( AbstractPulseTest.class ) ;


  private final ScheduledExecutorService scheduledExecutorService =
      ExecutorTools.singleThreadedScheduledExecutorServiceFactory(
          AbstractPulseTest.class.getSimpleName() ).create()
  ;

  @Override
  protected Pulse newPulse( final Pulse.Tickee tickee ) {
    return new Pulse.WithExecutorService(
        scheduledExecutorService, resolution, tickee ) ;
  }

  @Override
  protected Pulse.Resolution resolution() {
    return Pulse.Resolution.DECISECOND ;
  }

  @After
  public void tearDown() throws Exception {
    scheduledExecutorService.shutdownNow() ;
  }

}