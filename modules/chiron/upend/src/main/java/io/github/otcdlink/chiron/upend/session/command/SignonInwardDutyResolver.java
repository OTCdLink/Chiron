package io.github.otcdlink.chiron.upend.session.command;

import com.google.common.collect.ImmutableMap;
import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.codec.CommandBodyDecoder;
import io.github.otcdlink.chiron.codec.DecodeException;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.designator.Designator;
import io.github.otcdlink.chiron.upend.session.SignonInwardDuty;

public final class SignonInwardDutyResolver
    implements CommandBodyDecoder< Designator, SignonInwardDuty>
{
  private SignonInwardDutyResolver() { }

  private static final ImmutableMap< String, TargettedDecoder > DECODERS =
      ImmutableMap.< String, TargettedDecoder >builder()
      .put( "registerSession", SignonInwardDutyRegisterSession::decode )
      .put( "failedSignonAttempt", SignonInwardDutyFailedSignonAttempt::decode )
      .put( "resetSignonFailures", SignonInwardDutyResetSignonFailures::decode )
      .put( "signout", SignonInwardDutySignout::decode )
      .put( "signoutQuiet", SignonInwardDutySignoutQuiet::decode )
      .put( "signoutAll", SignonInwardDutySignoutAll::decode )
      .build()
  ;

  interface TargettedDecoder {
    Command< ? extends Designator, SignonInwardDuty> from(
        Designator endpointSpecific,
        PositionalFieldReader positionalFieldReader
    ) throws DecodeException ;
  }

  @Override
  public Command< Designator, SignonInwardDuty > decodeBody(
      final Designator designator,
      final String commandName,
      final PositionalFieldReader reader
  ) throws DecodeException {
    final TargettedDecoder targettedDecoder = DECODERS.get( commandName ) ;
    if( targettedDecoder == null ) {
      return null ;
    } else {
      return ( Command< Designator, SignonInwardDuty> )
          targettedDecoder.from( designator, reader ) ;
    }
  }

  public static final SignonInwardDutyResolver INSTANCE = new SignonInwardDutyResolver() ;
}
