package io.github.otcdlink.chiron.downend;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.middle.CommandFailureNotice;
import io.github.otcdlink.chiron.toolbox.ToStringTools;

/**
 * Callbacks for the progress of some {@link Command} sent upward.
 * Tracking relies on the propagation of a {@link Command.Tag} in the {@link Command}
 * sent as a downward update, as a consequence of the initial {@link Command}.
 * This requires special care from Upend implementation.
 */
public interface Tracker {

  /**
   * Given a {@link Tracker} instance, a call to this method occurs only once, and
   * no further call will occur.
   */
  void afterResponseHandled() ;

  /**
   *
   * Given a {@link Tracker} instance, a call to this method occurs only once, and
   * no further call will occur.
   */
  void afterRemoteFailure( CommandFailureNotice commandFailureNotice ) ;


  /**
   * Called after {@link #onConnectionLost()}, when the connection is here again.
   * This could be useful for re-enabling a widget in a graphical user interface.
   * Given a {@link Tracker} instance, a call to this method occurs only once, and
   * no further call will occur ({@link TrackerCurator} does nothing close to an automatic retry).
   */
  void onConnectionRestored() ;

  /**
   * Called if the connection broke.
   * This could be useful for disabling a widget in a graphical user interface.
   * Given a {@link Tracker} instance, a call to this method occurs only once, and
   * a call to {@link #onConnectionRestored()} should follow.
   */
  void onConnectionLost() ;

  void afterTimeout() ;

  class Adapter implements Tracker {
    @Override
    public void afterResponseHandled() { }

    @Override
    public void afterRemoteFailure( final CommandFailureNotice commandFailureNotice ) { }

    @Override
    public void onConnectionRestored() { }

    @Override
    public void onConnectionLost() { }

    @Override
    public void afterTimeout() { }

    @Override
    public String toString() {
      return ToStringTools.nameAndHash( this ) + "{}" ;
    }
  }

  Tracker NULL = new Adapter() {
    @Override
    public String toString() {
      return Tracker.class.getSimpleName() + "{NULL}" ;
    }
  } ;

}
