package io.github.otcdlink.chiron.toolbox.handover;

/**
 * Minimal contract for passing an arbitrary {@link OBJECT} to a receiver across threads
 * with or without blocking receiver's thread.
 */
public interface Handover< OBJECT > {

  void give( OBJECT object ) ;

}
