package io.github.otcdlink.chiron.middle;

import io.github.otcdlink.chiron.buffer.BytebufCoat;
import io.github.otcdlink.chiron.buffer.BytebufTools;
import io.github.otcdlink.chiron.codec.DecodeException;
import io.github.otcdlink.chiron.middle.session.FeatureMapper;
import io.github.otcdlink.chiron.middle.session.SecondaryCode;
import io.github.otcdlink.chiron.middle.session.SecondaryToken;
import io.github.otcdlink.chiron.middle.session.SessionIdentifier;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.Kind;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.PrimarySignon;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.Resignon;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.SecondarySignon;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.SecondarySignonNeeded;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.SessionValid;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.Signoff;
import io.github.otcdlink.chiron.middle.session.SessionLifecycle.SignonFailed;
import io.github.otcdlink.chiron.middle.session.SignonFailure;
import io.github.otcdlink.chiron.middle.session.SignonFailureNotice;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionLifecycleTest {

  @Test
  public void primarySignon() throws Exception {
    final PrimarySignon primarySignon = PrimarySignon.create( USER_LOGIN, USER_PASSWORD ) ;
    assertThat( primarySignon.login() ).isEqualTo( USER_LOGIN ) ;
    assertThat( primarySignon.password() ).isEqualTo( USER_PASSWORD ) ;
    assertThat( primarySignon.kind() ).isEqualTo( Kind.PRIMARY_SIGNON ) ;
    assertThat( primarySignon.keyValuePairs() ).containsValue( USER_LOGIN ) ;
    assertThat( primarySignon.keyValuePairs() ).containsValue( USER_PASSWORD ) ;
    serializeDeserialize( primarySignon ) ;
  }

  @Test
  public void primarySignonAugmented() throws Exception {
    final PrimarySignon primarySignon = PrimarySignon.create( USER_LOGIN, USER_PASSWORD ) ;
    final FeatureMapper featureMapper = FeatureMapper.from( new Delta( 1 ) ) ;
    final PrimarySignon augmented = SessionLifecycle.featurize( primarySignon, featureMapper ) ;
    assertThat( ( ( DeltaFeature ) augmented ).increment( 2 ) ).isEqualTo( 3 ) ;
    assertThat( ( ( DeltaFeature ) augmented ).decrement( 2 ) ).isEqualTo( 1 ) ;
  }

  @Test
  public void secondarySignonNeeded() throws Exception {
    final SecondarySignonNeeded secondarySignonNeeded = SecondarySignonNeeded.create(
        SECONDARY_TOKEN ) ;
    assertThat( secondarySignonNeeded.secondaryToken() ).isEqualTo( SECONDARY_TOKEN ) ;
    assertThat( secondarySignonNeeded.kind() ).isEqualTo( Kind.SECONDARY_SIGNON_NEEDED ) ;
    serializeDeserialize( secondarySignonNeeded ) ;
  }

  @Test
  public void secondarySignon() throws Exception {
    final SecondarySignon secondarySignon = SecondarySignon.create( 
        SECONDARY_TOKEN, SECONDARY_CODE ) ;
    assertThat( secondarySignon.secondaryCode() ).isEqualTo( SECONDARY_CODE ) ;
    assertThat( secondarySignon.secondaryToken() ).isEqualTo( SECONDARY_TOKEN ) ;
    assertThat( secondarySignon.kind() ).isEqualTo( Kind.SECONDARY_SIGNON ) ;
    serializeDeserialize( secondarySignon ) ;
  }

  @Test
  public void signonFailed() throws Exception {
    final SignonFailed signonFailed = SignonFailed.create( SIGNON_FAILURE_NOTICE ) ;
    assertThat( signonFailed.signonFailureNotice() ).isEqualTo( SIGNON_FAILURE_NOTICE ) ;
    assertThat( signonFailed.kind() ).isEqualTo( Kind.SIGNON_FAILED ) ;
    serializeDeserialize( signonFailed ) ;
  }

  @Test
  public void resignon() throws Exception {
    final Resignon resignon = Resignon.create( SESSION_IDENTIFIER ) ;
    assertThat( resignon.sessionIdentifier() ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( resignon.kind() ).isEqualTo( Kind.RESIGNON ) ;
    serializeDeserialize( resignon ) ;
  }

  @Test
  public void sessionCreated() throws Exception {
    final SessionValid sessionValid = SessionValid.create( SESSION_IDENTIFIER ) ;
    assertThat( sessionValid.sessionIdentifier() ).isEqualTo( SESSION_IDENTIFIER ) ;
    assertThat( sessionValid.kind() ).isEqualTo( Kind.SESSION_VALID ) ;
    serializeDeserialize( sessionValid ) ;
  }

  @Test
  public void signoff() throws Exception {
    final Signoff signoff = Signoff.create() ;
    assertThat( signoff ).isNotNull() ;
    serializeDeserialize( signoff ) ;
  }

  @Test
  public void kickout() throws Exception {
    final SessionLifecycle.Kickout kickout = SessionLifecycle.Kickout.create() ;
    assertThat( kickout ).isNotNull() ;
    serializeDeserialize( kickout ) ;
  }

  @Test
  public void timeout() throws Exception {
    final SessionLifecycle.Timeout timeout = SessionLifecycle.Timeout.create() ;
    assertThat( timeout ).isNotNull() ;
    serializeDeserialize( timeout ) ;
  }


// =======  
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( SessionLifecycleTest.class ) ;

  private static < PHASE extends SessionLifecycle.Phase > PHASE serializeDeserialize(
      final PHASE original
  ) throws DecodeException {
    serializeDeserializeAsString( original ) ;
    return serializeDeserializeInBuffer( original ) ;
  }

  private static < PHASE extends SessionLifecycle.Phase > PHASE serializeDeserializeAsString(
      final PHASE original
  ) throws DecodeException {
    final String serialized = SessionLifecycle.serialize( original ) ;
    final PHASE deserialized = SessionLifecycle.deserialize( serialized ) ;
    LOGGER.info( "Serialized " + original + " into:\n" + serialized ) ;
    assertThat( deserialized.asString() ).isEqualTo( original.asString() ) ;
    assertThat( deserialized ).isEqualTo( original ) ;
    assertThat( deserialized.keyValuePairs() ).isEqualTo( original.keyValuePairs() ) ;
    return deserialized ;
  }

  private static < PHASE extends SessionLifecycle.Phase > PHASE serializeDeserializeInBuffer(
      final PHASE original
  ) throws DecodeException {
    final ByteBuf byteBuf = Unpooled.buffer() ;
    final BytebufCoat coat = BytebufTools.coat( byteBuf ) ;

    SessionLifecycle.serialize( coat, original ) ;

    final PHASE deserialized = SessionLifecycle.deserialize( coat ) ;

    byteBuf.resetReaderIndex() ;
    LOGGER.debug( "Serialized " + original + " into:\n" + ByteBufUtil.prettyHexDump( byteBuf ) ) ;

    assertThat( deserialized.asString() ).isEqualTo( original.asString() ) ;
    assertThat( deserialized ).isEqualTo( original ) ;
    assertThat( deserialized.keyValuePairs() ).isEqualTo( original.keyValuePairs() ) ;
    return deserialized ;
  }

  private static final String USER_LOGIN = "userLogin" ;

  private static final String USER_PASSWORD = "userPassword" ;

  private static final SecondaryToken SECONDARY_TOKEN = new SecondaryToken( "someToken" ) ;

  private static final SecondaryCode SECONDARY_CODE = new SecondaryCode( "someCode" ) ;

  private static final SessionIdentifier SESSION_IDENTIFIER = new SessionIdentifier( "sessionId" ) ;

  private static final SignonFailureNotice SIGNON_FAILURE_NOTICE =
      new SignonFailureNotice( SignonFailure.INVALID_CREDENTIAL, "someMessage" ) ;

  public interface DeltaFeature {
    int increment( final int base ) ;

    int decrement( final int base ) ;
  }

  private static final class Delta implements DeltaFeature {
    private final int delta ;

    private Delta( final int delta ) {
      this.delta = delta ;
    }

    @Override
    public int increment( final int base ) {
      return base + delta ;
    }

    @Override
    public int decrement( final int base ) {
      return base - delta ;
    }
  }

}