package io.github.otcdlink.chiron.command;

import com.google.common.base.Preconditions;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.middle.CommandFailureDuty;
import io.github.otcdlink.chiron.middle.CommandFailureNotice;

import java.io.IOException;

/**
 *
 * @see io.github.otcdlink.chiron.middle.CommandFailureDuty
 */
public abstract class AbstractDownwardFailure<
    ENDPOINT_SPECIFIC,
    CALLABLE_RECEIVER extends CommandFailureDuty< ENDPOINT_SPECIFIC, NOTICE >,
    NOTICE extends CommandFailureNotice
>
    extends Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER >
{

  public final NOTICE notice ;

  protected AbstractDownwardFailure(
      final ENDPOINT_SPECIFIC endpointSpecific,
      final NOTICE notice
  ) {
    super( endpointSpecific ) ;
    this.notice = Preconditions.checkNotNull( notice ) ;

  }

  @Override
  public void callReceiver( final CALLABLE_RECEIVER Ø ) {
    throw new UnsupportedOperationException( "Not supposed to be called" ) ;
  }

  @Override
  public void encodeBody( final PositionalFieldWriter writer ) throws IOException {
    writer.writeIntegerPrimitive( notice.kind.ordinal() ) ;
    writer.writeDelimitedString( notice.message ) ;
  }
}
