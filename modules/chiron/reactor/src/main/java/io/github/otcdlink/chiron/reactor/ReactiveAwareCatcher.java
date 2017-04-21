package io.github.otcdlink.chiron.reactor;

import io.github.otcdlink.chiron.toolbox.catcher.Catcher;

import static com.google.common.base.Preconditions.checkNotNull;

public interface ReactiveAwareCatcher< COMMAND > extends Catcher {
  void processThrowable( COMMAND command, Throwable throwable ) ;

  /**
   * Make sure that any {@link Catcher} fits into the {@link ReactiveAwareCatcher} contract.
   */
  static< COMMAND > ReactiveAwareCatcher< COMMAND > wrap( final Catcher catcher ) {
    checkNotNull( catcher ) ;
    if( catcher instanceof ReactiveAwareCatcher ) {
      //noinspection unchecked
      return ( ReactiveAwareCatcher< COMMAND > ) catcher ;
    } else {
      return new ReactiveAwareCatcher< COMMAND >() {
        @Override
        public void processThrowable( final COMMAND command, final Throwable throwable ) {
          catcher.processThrowable( throwable ) ;
        }

        @Override
        public void processThrowable( final Throwable throwable ) {
          catcher.processThrowable( throwable ) ;
        }
      } ;
    }
  }
}
