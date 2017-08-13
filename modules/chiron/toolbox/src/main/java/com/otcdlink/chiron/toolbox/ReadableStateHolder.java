package com.otcdlink.chiron.toolbox;

/**
 * A read-only view of {@link StateHolder} that give access to instant state.
 * Atomic actions (like guaranteeing that a lambda executes while there is no change)
 * are not meant to be supported, since it could add a lot of overhead on the non-blocking
 * implementation.
 */
public interface ReadableStateHolder< STATE > {

  STATE get() ;

  @SuppressWarnings( "unchecked" )
  void checkOneOf( STATE... allowedStates ) ;

  void checkIn( Object owner, STATE allowedState ) ;

  void checkIn( Object owner, STATE allowed1, STATE allowed2 );

  @SuppressWarnings( "unchecked" )
  void checkIn(
      Object owner,
      STATE allowed1,
      STATE allowed2,
      STATE allowed3,
      STATE... allowedOthers
  ) ;

  @SuppressWarnings( "unchecked" )
  void checkInArray(
      Object owner,
      STATE... allowedOthers
  ) ;

  @SuppressWarnings( "unchecked" )
  boolean isOneOf( STATE... states );
}
