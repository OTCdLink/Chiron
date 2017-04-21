package io.github.otcdlink.chiron.designator;


import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.netty.RichHttpRequest;
import io.netty.channel.Channel;
import mockit.Injectable;
import mockit.StrictExpectations;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.function.BiFunction;

import static io.github.otcdlink.chiron.toolbox.netty.NettyTools.NULL_CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;

public class DesignatorTest {

  private static final String DESIGNATOR = ToStringTools.getNiceName( Designator.class ) ;

  @Test
  public void internal() throws Exception {
    final Designator internal = factory.internal() ;
    assertThat( internal.stamp ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( internal.cause ).isNull() ;
    assertThat( internal.tag ).isNull() ;
    assertThat( internal.sessionIdentifier ).isNull() ;
    assertThat( internal ).hasToString( DESIGNATOR + "{kind=INTERNAL;stamp=0:0}" ) ;
  }

  @Test
  public void phasing(
      @Injectable final Channel channel,
      @Injectable final RichHttpRequest richHttpRequest,
      @Injectable BiFunction<RichHttpRequest, Object, Object > renderer
  ) throws Exception {
    final Designator initial = factory.phasing( richHttpRequest, renderer ) ;

    final Designator derived0 = factory.internal( initial ) ;
    assertThat( derived0 ).isInstanceOf( RenderingAwareDesignator.class ) ;
    final RenderingAwareDesignator renderingAwareDesignator0 = ( RenderingAwareDesignator ) derived0 ;

    final Designator derived1 = factory.internal( derived0 ) ;
    assertThat( derived1 ).isInstanceOf( RenderingAwareDesignator.class ) ;
    final RenderingAwareDesignator renderingAwareDesignator1 = ( RenderingAwareDesignator ) derived1 ;

    new StrictExpectations() {{
      renderer.apply( ( RichHttpRequest ) any, "in" ) ; result = "out" ; times = 2 ;
    }} ;
    assertThat( renderingAwareDesignator1.renderFrom( fullHttpRequest(), "in" ) ).isEqualTo( "out" ) ;
    assertThat( renderingAwareDesignator1.renderFrom( fullHttpRequest(), "in" ) ).isEqualTo( "out" ) ;
  }

  @Test
  public void spontaneousWithSession() throws Exception {
    final Designator designator = factory.internal( SESSION_ID_0 ) ;
    assertThat( designator.stamp ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( designator.cause ).isNull() ;
    assertThat( designator.tag ).isNull() ;
    assertThat( designator.sessionIdentifier ).isEqualTo( SESSION_ID_0 ) ;
    assertThat( designator ).hasToString( DESIGNATOR + "{kind=INTERNAL;stamp=0:0;session=s000}" ) ;
  }

  @Test
  public void internalWithCause() throws Exception {
    final Designator cause = factory.internal( SESSION_ID_0 ) ;
    final Designator designator = factory.internal( cause ) ;
    assertThat( designator.stamp ).isEqualTo( TIMESTAMP_ONE ) ;
    assertThat( designator.cause ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( designator.tag ).isNull() ;
    assertThat( designator.sessionIdentifier ).isEqualTo( SESSION_ID_0 ) ;
    assertThat( designator )
        .hasToString( DESIGNATOR + "{kind=INTERNAL;stamp=0:1;cause=0:0;session=s000}" ) ;
  }

  @Test
  public void internalZero() throws Exception {
    final Designator cause = factory.internal( SESSION_ID_0 ) ;
    assertThat( cause.stamp ).describedAs( "Test health" ).isEqualTo( TIMESTAMP_ZERO ) ;
    final Designator designator = factory.internalZero( cause ) ;
    assertThat( designator.stamp ).isEqualTo( TIMESTAMP_ONE ) ;
    assertThat( designator.cause ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( designator.tag ).isNull() ;
    assertThat( designator.sessionIdentifier ).isNull() ;
    assertThat( designator ).hasToString( DESIGNATOR + "{kind=INTERNAL;stamp=0:1;cause=0:0}" ) ;
  }

  @Test
  public void returning() throws Exception {
    final Designator designator = factory.downward(
        SESSION_ID_0, TIMESTAMP_ONE, COMMAND_TAG ) ;
    assertThat( designator.stamp ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( designator.cause ).isEqualTo( TIMESTAMP_ONE ) ;
    assertThat( designator.tag ).isEqualTo( COMMAND_TAG ) ;
    assertThat( designator.sessionIdentifier ).isEqualTo( SESSION_ID_0 ) ;
    assertThat( designator )
        .hasToString( DESIGNATOR + "{kind=DOWNWARD;stamp=0:0;cause=0:1;tag=t;session=s000}" ) ;
  }

  @Test
  public void downward() throws Exception {
    final Designator upward = factory.upward( COMMAND_TAG, SESSION_ID_0 ) ;
    assertThat( upward.stamp ).isEqualTo( TIMESTAMP_ZERO ) ;
    final Designator designator = factory.downward( upward ) ;
    assertThat( designator.stamp ).isEqualTo( TIMESTAMP_ONE ) ;
    assertThat( designator.cause ).isEqualTo( TIMESTAMP_ZERO ) ;
    assertThat( designator.tag ).isEqualTo( COMMAND_TAG ) ;
    assertThat( designator.sessionIdentifier ).isEqualTo( SESSION_ID_0 ) ;
    assertThat( designator )
        .hasToString( DESIGNATOR + "{kind=DOWNWARD;stamp=0:1;cause=0:0;tag=t;session=s000}" ) ;
  }

  @Test
  public void exceptions() throws Exception {
    try( final AutoCloseableSoftAssertions soft = new AutoCloseableSoftAssertions() ) {
      soft.assertThatThrownBy( () -> factory.internal( ( Designator ) null ) ) ;
      soft.assertThatThrownBy( () -> factory.internalZero( null ) ) ;
      soft.assertThatThrownBy( () -> factory.downward( null, TIMESTAMP_ONE, COMMAND_TAG ) ) ;
    }
  }

  /**
   * Not sure we need to enforce this behavior. After all, we need it only for
   * {@link Designator} for dispatching CometD messages from always the same thread
   * given a {@link Designator#sessionIdentifier}.
   */
  @Test
  public void hashcodeForDesignatorUpward() throws Exception {
    final SessionIdentifier sessionIdentifier1 = new SessionIdentifier( "s" ) ;
    final Command.Tag commandTag1 = new Command.Tag( "1" ) ;
    final Command.Tag commandTag2 = new Command.Tag( "2" ) ;

    assertThat( commandTag1.hashCode() )
        .describedAs( "Test sanity: the two commandTags should have different hashCodes to test " +
            "they don't interfere" )
        .isNotEqualTo( commandTag2.hashCode() )
    ;


    final Designator origin1_1 = factory.upward( commandTag1, sessionIdentifier1 ) ;
    final Designator origin2_1 = factory.upward( commandTag2, sessionIdentifier1 ) ;
    assertThat( origin1_1.hashCode() ).isEqualTo( origin2_1.hashCode() ) ;
    assertThat( origin1_1.hashCode() ).isEqualTo( sessionIdentifier1.hashCode() ) ;

  }

  @Test
  public void hashCodeForDesignatorDownwardUsesSessionIdentifier() throws Exception {

    final SessionIdentifier sessionIdentifier1 = new SessionIdentifier( "s" ) ;
    final Designator cause1 = factory.internal() ;
    final Designator destination_1 =
        factory.downward( sessionIdentifier1, cause1.stamp ) ;

    final Designator cause2 = factory.internal() ;
    final SessionIdentifier sessionIdentifier2 = new SessionIdentifier( ( "S2" ) ) ;
    final Designator destination_2 =
        factory.downward( sessionIdentifier2, cause2.stamp ) ;

    assertThat( destination_2.hashCode() )
        .describedAs( "Test sanity: get sure that destination is taken in account " +
            "by using two non-equal ones" )
        .isNotEqualTo( destination_1.hashCode() ) ;

    final Designator origin1_1 =
        factory.upward( new Command.Tag( "t" ), sessionIdentifier1 ) ;

    assertThat( origin1_1.hashCode() ).isEqualTo( destination_1.hashCode() ) ;
    assertThat( origin1_1.hashCode() ).isNotEqualTo( destination_2.hashCode() ) ;


  }

// =======
// Fixture
// =======

  private static final Clock CLOCK = () -> Stamp.FLOOR_MILLISECONDS;

  private static final Stamp TIMESTAMP_ZERO =
      Stamp.raw( Stamp.FLOOR_MILLISECONDS, 0 ) ;

  private static final Stamp TIMESTAMP_ONE =
      Stamp.raw( Stamp.FLOOR_MILLISECONDS, 1 ) ;

  private static final SessionIdentifier SESSION_ID_0 = new SessionIdentifier( "s000" ) ;
  private static final SessionIdentifier SESSION_ID_1 = new SessionIdentifier( "s111" ) ;

  private static final Command.Tag COMMAND_TAG = new Command.Tag( "t" ) ;

  private final Stamp.Generator generator = new Stamp.Generator( CLOCK ) ;

  private final Designator.Factory factory = new Designator.Factory( generator ) ;

  private static RichHttpRequest fullHttpRequest() {
    return RichHttpRequest.from(
        "/",
        NULL_CHANNEL,
        InetSocketAddress.createUnresolved( "0.0.0.0", 0 ),
        InetSocketAddress.createUnresolved( "0.0.0.0", 0 ),
        false
    ) ;
  }


}