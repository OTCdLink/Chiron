package io.github.otcdlink.chiron.reactor;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.toolbox.ToStringTools;

public final class FakeCommand {

  public final Kind kind ;
  public final Integer parameter ;
  public final FakeCommand cause ;


  public FakeCommand( final Kind kind ) {
    this( kind, null ) ;
  }

  public FakeCommand( final Kind kind, final Integer parameter ) {
    this( kind, parameter, null ) ;
  }

  public FakeCommand( final Kind kind, final Integer parameter, final FakeCommand cause ) {
    this.parameter = parameter ;
    this.kind = Preconditions.checkNotNull( kind ) ;
    this.cause = cause ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + '{' +
        kind.name() +
        ( parameter == null ? "" : ";" + parameter ) +
        ( cause == null ? "" : ";cause=" + ToStringTools.compactHashForNonNull( cause ) ) +
        '}'
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

    final FakeCommand that = ( FakeCommand ) other ;

    if( kind != that.kind ) {
      return false ;
    }
    return ! ( parameter != null ? ! parameter.equals( that.parameter ) : that.parameter != null ) ;

  }

  @Override
  public int hashCode() {
    int result = kind.hashCode() ;
    result = 31 * result + ( parameter != null ? parameter.hashCode() : 0 ) ;
    return result ;
  }

  public enum Kind {
    UPWARD_MONITORING,
    DOWNWARD_MONITORING,
    UPWARD_PROPOSAL,
    DOWNWARD_PROPOSAL,
    INTERNAL_DAYBREAK,
    UPWARD_CHANGE_PASSWORD,
    UPWARD_GLOBAL_ACTIVATION,
    INTERNAL_CHANGE_PASSWORD,
    INTERNAL_HASH_PASSWORD,
    INTERNAL_SESSION_CREATE,
    INTERNAL_SESSION_CREATED,
    UPWARD_PRECONFIRMATION,
    INTERNAL_SEND_EMAIL,
    INTERNAL_SET_THROTTLING,
    INTERNAL_THROTTLER_DELAY,
    DOWNWARD_START_GLOBAL_ACTIVATION,
    DOWNWARD_CHANGE_PASSWORD,
    DOWNWARD_FAILURE,
    MAGIC_PERSISTENCE_SUCCESS,
    ;
  }

  public static FakeCommand upwardProposal( final Integer integer ) {
    return new FakeCommand( Kind.UPWARD_PROPOSAL, integer ) ;
  }

  public static FakeCommand upwardMonitoring() {
    return new FakeCommand( Kind.UPWARD_MONITORING ) ;
  }

  public static FakeCommand upwardGlobalActivation() {
    return new FakeCommand( Kind.UPWARD_GLOBAL_ACTIVATION ) ;
  }

  public static FakeCommand downwardProposal( final Integer integer ) {
    return new FakeCommand( Kind.DOWNWARD_PROPOSAL, integer ) ;
  }

  public static FakeCommand downwardMonitoring() {
    return new FakeCommand( Kind.DOWNWARD_MONITORING ) ;
  }

  public static FakeCommand upwardChangePassword( final Integer integer ) {
    return new FakeCommand( Kind.UPWARD_CHANGE_PASSWORD, integer ) ;
  }

  public static FakeCommand upwardPreconfirmation( final Integer integer ) {
    return new FakeCommand( Kind.UPWARD_PRECONFIRMATION, integer ) ;
  }

  public static FakeCommand internalSendEmail( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_SEND_EMAIL, integer ) ;
  }

  public static FakeCommand internalSessionCreate( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_SESSION_CREATE, integer ) ;
  }

  public static FakeCommand internalSessionCreated( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_SESSION_CREATED, integer ) ;
  }

  public static FakeCommand internalHashPassword( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_HASH_PASSWORD, integer ) ;
  }

  public static FakeCommand internalChangePassword( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_CHANGE_PASSWORD, integer ) ;
  }

  public static FakeCommand internalSetThrottling( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_SET_THROTTLING, integer ) ;
  }

  public static FakeCommand internalThrottlerDelay( final Integer integer ) {
    return new FakeCommand( Kind.INTERNAL_THROTTLER_DELAY, integer ) ;
  }

  public static FakeCommand downwardChangePassword( final Integer integer ) {
    return new FakeCommand( Kind.DOWNWARD_CHANGE_PASSWORD, integer ) ;
  }

  public static FakeCommand downwardStartGlobalActivation() {
    return new FakeCommand( Kind.DOWNWARD_START_GLOBAL_ACTIVATION ) ;
  }

  public static FakeCommand downwardFailure() {
    return new FakeCommand( Kind.DOWNWARD_FAILURE ) ;
  }

  public static FakeCommand daybreak() {
    return new FakeCommand( Kind.INTERNAL_DAYBREAK ) ;
  }

}
