package io.github.otcdlink.chiron.downend;

import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.middle.CommandFailureNotice;

/**
 * Tells if there is at least one {@link Command} currently sent, for which
 * {@link CommandTransceiver} is waiting for a matching response.
 * A graphical user interface can use this to display an indicator of what's going on.
 */
public enum CommandInFlightStatus {

  /**
   * There was at least one {@link #IN_FLIGHT} {@link Command} but now there is none.
   */
  QUIET,

  /**
   * There is at least one {@link Command} which was sent upward, for which no matching
   * {@link Command.Tag} has been received yet.
   */
  IN_FLIGHT,

  /**
   * Some {@link Command} did fail (causing a call to
   * {@link Tracker#afterRemoteFailure(CommandFailureNotice)} or
   * {@link Tracker#afterTimeout()}).
   * In the case of one single in-fligh {@link Command}, after receiving
   * {@link #SOME_COMMAND_FAILED}, the
   * {@link CommandTransceiver.ChangeWatcher#inFlightStatusChange(io.github.otcdlink.chiron.downend.CommandInFlightStatus)}
   * method will receive a {@link #QUIET}.
   */
  SOME_COMMAND_FAILED, ;
}
