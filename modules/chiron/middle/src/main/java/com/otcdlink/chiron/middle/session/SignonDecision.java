package com.otcdlink.chiron.middle.session;

import java.util.Objects;

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
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }
    final SignonDecision< ? > that = ( SignonDecision< ? > ) other ;
    return Objects.equals( userIdentity, that.userIdentity ) &&
        Objects.equals( signonFailureNotice, that.signonFailureNotice ) ;
  }

  @Override
  public int hashCode() {
    return Objects.hash( userIdentity, signonFailureNotice ) ;
  }

}
