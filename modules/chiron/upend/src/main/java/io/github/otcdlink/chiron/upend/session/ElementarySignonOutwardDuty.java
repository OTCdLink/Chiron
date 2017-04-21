package io.github.otcdlink.chiron.upend.session;

import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.middle.PhoneNumber;
import io.github.otcdlink.chiron.middle.session.SignonDecision;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;

/**
 * A restricted view of {@link SignonOutwardDuty} which is useful in some edge cases.
 */
public interface ElementarySignonOutwardDuty {

  /**
   * The {@link SignonDecision} carries an optional reference to a {@link SignableUser},
   * which enriches {@link SignableUser#login()} with a {@link PhoneNumber}.
   *
   * @see SignonInwardDuty#primarySignonAttempt(Designator, String, String)
   */
  void primarySignonAttempted(
      Designator designatorInternal,
      SignonDecision< SignableUser > signonDecision
  ) ;

  /**
   * @see SignonInwardDuty#secondarySignonAttempt(Designator, String)
   */
  void secondarySignonAttempted(
      Designator designatorInternal,
      SignonFailureNotice signonFailureNotice
  ) ;
}
