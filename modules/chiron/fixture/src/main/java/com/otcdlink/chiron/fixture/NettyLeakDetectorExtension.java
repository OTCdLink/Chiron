package com.otcdlink.chiron.fixture;

import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.util.ResourceLeakDetector.Level.PARANOID;

public final class NettyLeakDetectorExtension
    implements BeforeTestExecutionCallback, AfterTestExecutionCallback
{

  private static final Logger LOGGER = LoggerFactory.getLogger( NettyLeakDetectorExtension.class ) ;

  private static final String STORE_KEY = NettyLeakDetectorExtension.class.getSimpleName() +
      ":initialLeakDetectorLevel";

  @Override
  public void beforeTestExecution( final ExtensionContext extensionContext ) {
    final ExtensionContext.Store store = store( extensionContext ) ;
    store.put( STORE_KEY, ResourceLeakDetector.getLevel() ) ;
    setLevel( PARANOID ) ;
  }

  @Override
  public void afterTestExecution( final ExtensionContext extensionContext ) {
    System.gc() ;
    final ResourceLeakDetector.Level level =
        store( extensionContext ).get( STORE_KEY, ResourceLeakDetector.Level.class ) ;
    if( level != null ) {
      setLevel( level ) ;
    }
  }

  private static ExtensionContext.Store store( ExtensionContext extensionContext ) {
    return extensionContext.getStore(
        ExtensionContext.Namespace.create( extensionContext.getUniqueId() ) );
  }

  private static void setLevel( final ResourceLeakDetector.Level level ) {
    ResourceLeakDetector.setLevel( level ) ;
    LOGGER.info( "Level of " + ResourceLeakDetector.class.getSimpleName() + " set to " +
        level + "." ) ;
  }
}
