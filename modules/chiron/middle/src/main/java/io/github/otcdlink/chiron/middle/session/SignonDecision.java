package io.github.otcdlink.chiron.middle.session;

import io.github.otcdlink.chiron.toolbox.ComparatorTools;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class SignonDecision< USER_IDENTITY > {
  public final USER_IDENTITY userIdentity ;
  public final SignonFailureNotice signonFailureNotice;

  public SignonDecision( final USER_IDENTITY userIdentity ) {
    this.userIdentity = checkNotNull( userIdentity ) ;
    signonFailureNotice = null ;
  }

  public SignonDecision( final SignonFailureNotice signonFailureNotice ) {
    this.signonFailureNotice = checkNotNull( signonFailureNotice ) ;
    this.userIdentity = null ;
  }

  public SignonDecision(
      final USER_IDENTITY userIdentity,
      final SignonFailureNotice signonFailureNotice
  ) {
    this.userIdentity = checkNotNull( userIdentity ) ;
    this.signonFailureNotice = checkNotNull( signonFailureNotice ) ;
    checkArgument(
        SignonFailure.WITH_USER_IDENTITY.contains( signonFailureNotice.kind ),
        "Signon failure notice %s should support user identity (be one of %s)",
        signonFailureNotice, SignonFailure.WITH_USER_IDENTITY
    ) ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + "userIdentity=" + userIdentity
        + ";signonFailureNotice=" + signonFailureNotice
        + "}"
    ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( other == this ) {
      return true ;
    }
    if( other.getClass() != this.getClass() ) {
      return false ;
    }
    final SignonDecision that = ( SignonDecision ) other ;
    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int hashCode() {
    return
          userIdentity.hashCode()
        + signonFailureNotice.hashCode() * 31
    ;
  }

  public static final Comparator<SignonDecision> COMPARATOR
      = new ComparatorTools.WithNull<SignonDecision>() {
        @Override
        protected int compareNoNulls( final SignonDecision first, final SignonDecision second ) {
          final int signonFailureNoticeComparison = SignonFailureNotice.COMPARATOR.compare(
              first.signonFailureNotice, second.signonFailureNotice ) ;
          if( signonFailureNoticeComparison == 0 ) {
            final int userIdentityComparison = ComparatorTools.LAST_CHANCE_COMPARATOR.compare(
                first.userIdentity, second.userIdentity ) ;
            return userIdentityComparison ;
          } else {
            return signonFailureNoticeComparison ;
          }
        }
      }
  ;
}
