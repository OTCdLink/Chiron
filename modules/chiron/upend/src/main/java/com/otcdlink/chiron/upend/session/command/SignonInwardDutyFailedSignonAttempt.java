package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.upend.session.SignonAttempt;
import com.otcdlink.chiron.upend.session.SignonInwardDuty;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

@Command.Description( name="failedSignonAttempt" )
public class SignonInwardDutyFailedSignonAttempt
    extends Command< Designator, SignonInwardDuty>
{

  private final String login ;
  private final SignonAttempt signonAttempt ;

  public SignonInwardDutyFailedSignonAttempt(
      final Designator designatorInternal,
      final String login,
      final SignonAttempt signonAttempt
  ) {
    super( designatorInternal ) ;
    this.login = checkNotNull( login ) ;
    this.signonAttempt = checkNotNull( signonAttempt ) ;
  }

  @Override
  public void callReceiver( final SignonInwardDuty signonInwardDuty ) {
    signonInwardDuty.failedSignonAttempt( endpointSpecific, login, signonAttempt ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeDelimitedString( login ) ;
    writer.writeIntegerPrimitive( signonAttempt.ordinal() ) ;
  }

  public static SignonInwardDutyFailedSignonAttempt decode(
      final Designator designator,
      final PositionalFieldReader reader
  ) throws DecodeException {
    return new SignonInwardDutyFailedSignonAttempt(
        ( Designator ) designator,
        reader.readDelimitedString(),
        SignonAttempt.fromOrdinal( reader.readIntegerPrimitive() )
    ) ;
  }

}
