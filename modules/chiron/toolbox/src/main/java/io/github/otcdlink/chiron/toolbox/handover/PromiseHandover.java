package io.github.otcdlink.chiron.toolbox.handover;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link Handover} based on a {@link Promise}, so callbacks occur in a
 * {@link Channel} thread.
 *
 */
public class PromiseHandover< OBJECT > implements Handover< OBJECT > {

  private final Promise< OBJECT > promise ;

  public PromiseHandover(
      final Promise< OBJECT > promise,
      final Consumer< OBJECT > onSuccess,
      final Consumer< Throwable > onFailure
  ) {
    this.promise = checkNotNull( promise ) ;
    promise.addListener( future -> {
      final Throwable cause = future.cause() ;
      if( cause == null ) {
        final OBJECT object = ( OBJECT ) future.get() ;
        onSuccess.accept( object ) ;
      } else {
        onFailure.accept( cause ) ;
      }
    } ) ;
  }

  @Override
  public void give( final OBJECT object ) {
    promise.setSuccess( object ) ;
  }

  public void fail( final Throwable cause ) {
    promise.setFailure( cause ) ;
  }

}
