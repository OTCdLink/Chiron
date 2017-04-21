package io.github.otcdlink.chiron.middle.session;

import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.middle.EnumeratedMessageKind;

public enum SignonFailure implements EnumeratedMessageKind {

  UNEXPECTED( false, "Unexpected error, restart the application" ),
  CONNECTION_REFUSED( "Connection refused by Daemon" ),
  UNSUPPORTED_SERVER_VERSION( false, "Unsupporter Daemon version" ),
  MISSING_CREDENTIAL( "Missing login/password" ),
  INVALID_CREDENTIAL( "Invalid login/password" ),
  MISSING_SECONDARY_CODE( "Missing Secondary Code" ),
  INVALID_SECONDARY_CODE( "Invalid Secondary Code" ),
  UNEXPECTED_SECONDARY_CODE(
      "Unexpected Secondary Code, there is no Secondary Authenticator supporting this" ),
  TOO_MANY_ATTEMPTS( false, "Too many failed login attempts" ),

  /**
   * {@code AuthenticationFailure} class supposed to stay Upend.
   */
  SECONDARY_AUTHENTICATION_GENERIC_FAILURE( "Secondary Authentication failed" ),


  /**
   * @deprecated we discard {@link SecondaryToken}s as soon as possible so there is no way
   *     to know if they are expired.
   */
  SECONDARY_AUTHENTICATION_PERIOD_EXPIRED( "Secondary authentication period expired" ),

  INVALID_SECONDARY_TOKEN( "Secondary Token unknown or expired" ),



  SESSION_ALREADY_EXISTS( "Session already open" ),
  SESSION_ALREADY_ATTRIBUTED( "Session already attributed" ),

  CHANNEL_ALREADY_REGISTERED(
      false,
      "This connection is already registered. " +
          "This is an internal error, please restart the application."
  ),
  CHANNEL_ALREADY_SET(
      false,
      "There is already a network connection for this session. " +
          "This is an internal error, please restart the application."
  ),
  UNKNOWN_SESSION( false, "Unknown Session" ),
  UNMATCHED_NETWORK_ADDRESS(
      false,
      "Trying to attach to a Session from a different network address " +
          "than the one the Session was created with"
  ),
  REMOTE_ADDRESS_BLACKLISTED_AFTER_FAILED_ASSOCIATION(
      false,
      "Trying to attach to a Session from a network address that already failed to attach; " +
          "this might be a spoofing attempt so this address is temporarily blacklisted " +
          "as a security measure"
  ),

  ;

  public final boolean recoverable ;
  private final String description ;

  private SignonFailure( final String description ) {
    this( true, description ) ;
  }

  private SignonFailure( final boolean recoverable, final String description ) {
    this.recoverable = recoverable ;
    this.description = description ;
  }

  @Override
  public String description() {
    return description ;
  }


  public static final ImmutableSet< SignonFailure > WITH_USER_IDENTITY
      = ImmutableSet.of( INVALID_CREDENTIAL, INVALID_SECONDARY_CODE, TOO_MANY_ATTEMPTS ) ;


}
