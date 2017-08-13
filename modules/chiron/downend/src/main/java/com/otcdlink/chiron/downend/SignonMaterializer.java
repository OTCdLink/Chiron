package com.otcdlink.chiron.downend;


import com.otcdlink.chiron.middle.session.SecondaryCode;
import com.otcdlink.chiron.middle.session.SignonFailureNotice;
import com.otcdlink.chiron.toolbox.Credential;

import java.util.function.Consumer;

/**
 * The contract of something interacting with the human user to request credential
 * and (at server injunction) a disposable code for Secondary Authentication.
 */
public interface SignonMaterializer {


  /**
   * Opens the dialog, and sends back a {@link Credential} or {@code null} if cancelled.
   */
  void readCredential( Consumer< Credential > credentialConsumer ) ;

  /**
   * Shows field for secondary code and disables those for {@link #readCredential(Consumer)}.
   * Sends back a {@link SecondaryCode} or {@code null} if cancelled.
   */
  void readSecondaryCode( Consumer< SecondaryCode > secondaryCodeConsumer ) ;

  /**
   * Don't allow anything but Cancel, so User can read some error message.
   * This method doesn't block.
   */
  void waitForCancellation( final Runnable afterCancelled ) ;

  /**
   * Displays the blue message.
   * Also activates the spinwheel.
   */
  void setProgressMessage( String message ) ;

  /**
   * Red message.
   */
  void setProblemMessage( SignonFailureNotice signonFailureNotice ) ;


  /**
   * Closes the dialog as soon as possible (considering a method call happening asynchronously).
   */
  void done() ;



}
