package com.otcdlink.chiron.middle;

import com.otcdlink.chiron.command.AbstractDownwardFailure;
import com.otcdlink.chiron.command.Command;

/**
 * For reporting errors to the Downend.
 * @see AbstractDownwardFailure
 */
public interface CommandFailureDuty<
    ENDPOINT_SPECIFIC,
    COMMAND_FAILURE_NOTICE extends CommandFailureNotice
> {

  /**
   * Don't do anything in the implementation as only the Upend calls this method with
   * a {@link Command.Tag} that refers to another command.
   */
  void failure( ENDPOINT_SPECIFIC endpointSpecific, COMMAND_FAILURE_NOTICE commandFailureNotice ) ;
}
