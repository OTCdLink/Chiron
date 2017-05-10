package io.github.otcdlink.chiron.fixture;

import io.netty.util.ResourceLeakDetector;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.util.ResourceLeakDetector.Level.PARANOID;

public final class NettyLeakDetectorRule implements TestRule {

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyLeakDetectorRule.class ) ;

  private static final ResourceLeakDetector.Level INITIAL_LEAK_DETECTOR_LEVEL =
      ResourceLeakDetector.getLevel() ;

  @Override
  public Statement apply( Statement base, Description description ) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        beforeStatementEvaluation() ;
        try {
          base.evaluate() ;
        } finally {
          afterStatementEvaluation() ;
        }
      }
    };
  }

  protected void beforeStatementEvaluation() throws Exception {
    setLevel( PARANOID ) ;
  }

  protected void afterStatementEvaluation() throws Exception {
    System.gc() ;
    ResourceLeakDetector.setLevel( INITIAL_LEAK_DETECTOR_LEVEL ) ;
  }

  private static void setLevel( final ResourceLeakDetector.Level level ) {
    ResourceLeakDetector.setLevel( level ) ;
    LOGGER.info( "Level of " + ResourceLeakDetector.class.getSimpleName() + " set to " +
        level + "." ) ;
  }
}
