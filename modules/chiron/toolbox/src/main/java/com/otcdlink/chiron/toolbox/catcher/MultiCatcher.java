package com.otcdlink.chiron.toolbox.catcher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MultiCatcher extends LoggingCatcher {

  private final ImmutableList<Catcher> catchers ;

  public MultiCatcher( final Catcher... catchers ) {
    this( ImmutableList.copyOf( catchers ) ) ;
  }

  public MultiCatcher( final ImmutableList< Catcher > catchers ) {
    this.catchers = Preconditions.checkNotNull( catchers ) ;
  }

  @Override
  public void processThrowable( final Throwable throwable ) {
    for( final Catcher catcher : catchers ) {
      catcher.processThrowable( throwable ) ;
    }
  }

}
