package com.otcdlink.chiron.upend.tier;

import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.codec.DecodeException;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.middle.ChannelTools;
import com.otcdlink.chiron.middle.CommandAssert;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class CommandWebsocketDecoderUpendTierTest {

  @Test
  public void decode(
      @Injectable final Designator.Factory designatorFactory
  ) throws Exception {

    final CommandWebsocketCodecUpendTier<
                InboundCallableReceiver,
                OutboundCallableReceiver
            > channelHandler = new CommandWebsocketCodecUpendTier<>(
        newBodyDecoder(),
        designatorFactory
    ) ;

    final EmbeddedChannel embeddedChannel = new EmbeddedChannel( channelHandler ) ;
    embeddedChannel.attr( ChannelTools.SESSION_KEY ).set( SESSION_IDENTIFIER ) ;

    new StrictExpectations() {{
      designatorFactory.upward( TAG, SESSION_IDENTIFIER ) ;
      result = DUMMY_UPWARD_COMMAND.endpointSpecific ;
    }} ;

    final TextWebSocketFrame inboundTextWebSocketFrame = new TextWebSocketFrame(
        DUMMY_UPWARD_COMMAND.description().name() + ' ' +
        TAG.asString() + ' ' +
        Integer.toString( DUMMY_UPWARD_COMMAND.integerPrimitive ) + ' ' +
        DUMMY_UPWARD_COMMAND.nullableString + ' '
    ) ;

    embeddedChannel.writeInbound( inboundTextWebSocketFrame ) ;

    assertThat( embeddedChannel.inboundMessages() ).hasSize( 1 ) ;
    CommandAssert.assertThat( ( Command ) embeddedChannel.inboundMessages().remove() )
        .isEquivalentTo( DUMMY_UPWARD_COMMAND ) ;
  }


  @Test
  public void encode(
      @Injectable final Designator.Factory designatorFactory
  ) throws Exception {

    final CommandWebsocketCodecUpendTier<
                InboundCallableReceiver,
                OutboundCallableReceiver
            > channelHandler = new CommandWebsocketCodecUpendTier<>(
        newBodyDecoder(),
        designatorFactory
    ) ;

    final EmbeddedChannel embeddedChannel = new EmbeddedChannel( channelHandler ) ;
    embeddedChannel.attr( ChannelTools.SESSION_KEY ).set( SESSION_IDENTIFIER ) ;

    embeddedChannel.writeOutbound( DUMMY_DOWNWARD_COMMAND ) ;

    assertThat( embeddedChannel.outboundMessages() ).hasSize( 1 ) ;
    assertThat( ( ( TextWebSocketFrame ) embeddedChannel.outboundMessages().remove() ).text() )
        .isEqualTo(
            DUMMY_UPWARD_COMMAND.description().name() + ' ' +
            DUMMY_UPWARD_COMMAND.endpointSpecific.tag.asString() + ' ' +
            Integer.toString( DUMMY_UPWARD_COMMAND.integerPrimitive ) + ' ' +
            DUMMY_UPWARD_COMMAND.nullableString + ' '
        )
    ;
  }

  @Test( expected = AssertionError.class )
  public void nonEquivalence() throws Exception {
    CommandAssert.assertThat( DUMMY_UPWARD_COMMAND ).isEquivalentTo( DUMMY_UPWARD_COMMAND_2 ) ;
  }

// =======
// Fixture
// =======


  private interface InboundCallableReceiver {
    void intAndString( int i, String s ) ;
  }

  /**
   * The test doesn't care about this one, it's just for typing {@link Command}s correctly.
   */
  private interface OutboundCallableReceiver { }

  private static< ENDPOINT_SPECIFIC > CommandBodyDecoder< ENDPOINT_SPECIFIC, InboundCallableReceiver>
  newBodyDecoder() {
    return newBodyDecoder( ImmutableMap.of( "dummy", DummyCommand::decode ) ) ;
  }

  private static< ENDPOINT_SPECIFIC > CommandBodyDecoder< ENDPOINT_SPECIFIC, InboundCallableReceiver >
  newBodyDecoder(
      final ImmutableMap< String, TargettedCommandDecoder< ENDPOINT_SPECIFIC, InboundCallableReceiver > >
          targettedDecoders
  ) {
    return ( endpointSpecific, commandName, positionalFieldReader ) ->
      targettedDecoders.get( commandName ).decode( endpointSpecific, positionalFieldReader ) ;
  }

  interface TargettedCommandDecoder< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > {
    Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > decode(
        final ENDPOINT_SPECIFIC endpointSpecific,
        final PositionalFieldReader positionalFieldReader
    ) throws DecodeException ;
  }


  @Command.Description( name = "dummy" )
  private static class DummyCommand< ENDPOINT_SPECIFIC >
      extends Command< ENDPOINT_SPECIFIC, InboundCallableReceiver>
  {
    private final int integerPrimitive ;
    private final String nullableString ;

    public DummyCommand(
        final ENDPOINT_SPECIFIC endpointSpecific,
        final int integerPrimitive,
        final String nullableString
    ) {
      super( endpointSpecific ) ;
      this.integerPrimitive = integerPrimitive ;
      this.nullableString = nullableString;
    }

    @Override
    public void callReceiver( final InboundCallableReceiver inboundCallableReceiver ) {
      inboundCallableReceiver.intAndString( integerPrimitive, nullableString ) ;
    }

    @Override
    public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
      positionalFieldWriter.writeIntegerPrimitive( integerPrimitive ) ;
      positionalFieldWriter.writeNullableString( nullableString ) ;
    }

    public static< ENDPOINT_SPECIFIC > DummyCommand< ENDPOINT_SPECIFIC > decode(
        final ENDPOINT_SPECIFIC endpointSpecific,
        final PositionalFieldReader positionalFieldReader
    ) throws DecodeException {
      return new DummyCommand<>(
          endpointSpecific,
          positionalFieldReader.readIntegerPrimitive(),
          positionalFieldReader.readNullableString()
      ) ;
    }
  }

  private static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "Stuvwxyz" ) ;
  private static final Stamp STAMP = Stamp.raw( Stamp.FLOOR_MILLISECONDS, 1 ) ;
  private static final Command.Tag TAG = new Command.Tag( "Tuvwx" ) ;
  private static final Designator DESIGNATOR_UPWARD = DesignatorForger.newForger()
      .session( SESSION_IDENTIFIER ).tag( TAG ).instant( STAMP.timestampUtc() ).upward() ;

  private static final Designator DESIGNATOR_DOWNWARD = DesignatorForger.newForger()
      .session( SESSION_IDENTIFIER ).tag( TAG ).instant( STAMP.timestampUtc() ).downward() ;

  private static final DummyCommand< Designator > DUMMY_UPWARD_COMMAND =
      new DummyCommand<>( DESIGNATOR_UPWARD, 1, "One" ) ;

  private static final DummyCommand< Designator > DUMMY_DOWNWARD_COMMAND =
      new DummyCommand<>( DESIGNATOR_DOWNWARD, 1, "One" ) ;

  private static final DummyCommand< Designator > DUMMY_UPWARD_COMMAND_2 =
      new DummyCommand<>( DESIGNATOR_UPWARD, 2, "Two" ) ;


}