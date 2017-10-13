package com.otcdlink.chiron.toolbox.concurrent.freeze;

public interface FreezeControl< FROZEN > {
  void continueWhenWarm() ;
  void freeze( final FROZEN frozen ) ;
}
