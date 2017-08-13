package com.otcdlink.chiron.toolbox.concurrent.freeze;

public interface ThreadFreeze< FROZEN > {
  void unfreeze() ;
  FROZEN frozen() ;
}
