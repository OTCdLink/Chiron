package com.otcdlink.chiron.upend.session.command;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

public final class SignonInwardDutyResolver
    implements CommandBodyDecoder< Designator, SignonInwardDuty >
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
    ) throws DecodeException;
  }

  @Override
  public Command< Designator, SignonInwardDuty> decodeBody(
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
