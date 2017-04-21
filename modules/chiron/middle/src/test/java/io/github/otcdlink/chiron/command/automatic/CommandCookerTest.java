package io.github.otcdlink.chiron.command.automatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.buffer.BytebufCoat;
import io.github.otcdlink.chiron.buffer.BytebufTools;
import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.command.Stamp;
import io.github.otcdlink.chiron.command.codec.Codec;
import io.github.otcdlink.chiron.toolbox.StringWrapper;
import io.github.otcdlink.chiron.toolbox.clock.UpdateableClock;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import mockit.Expectations;
import mockit.Injectable;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore( "Work in progress, broke everything when introducing Javassist." )
@SuppressWarnings( "TestMethodWithIncorrectSignature" )
public class CommandCookerTest {

  @Test
  public void encodeDecode( @Injectable final SomeFeature< Endpoint > featureMock ) throws Exception {
    final CommandCooker< Endpoint, SomeFeature< Endpoint > > cooker = new CommandCooker<>(
        PREAMBLE_CODEC_FACTORY,
        FEATURE_REGISTRY,
        CODECS,
        COMMAND_NAME_PREFIX,
        null,
        new Stamp.Generator( clock )
    ) ;
    LOGGER.info( "Created " + cooker + "." ) ;

    final io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint >> echoCommand =
        createEchoCommand( cooker, "hello there" ) ;
    final ByteBuf byteBuf = Unpooled.buffer() ;
    final BytebufCoat coat = BytebufTools.threadLocalRecyclableCoating().coat( byteBuf ) ;
    cooker.encodeTo( echoCommand, coat ) ;

    final io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint >> decoded =
        cooker.decodeFrom( coat ) ;

    new Expectations() { {
      featureMock.echo( ENDPOINT, "hello there" ) ;
    } } ;
    decoded.callReceiver( featureMock ) ;

  }

  @Test
  public void receiver( @Injectable final SomeFeature< Endpoint > featureMock ) throws Exception {

    final CommandCooker< Endpoint, SomeFeature< Endpoint >> cooker = new CommandCooker<>(
        PREAMBLE_CODEC_FACTORY,
        FEATURE_REGISTRY,
        CODECS,
        COMMAND_NAME_PREFIX,
        null,
        new Stamp.Generator( clock )
    ) ;
    LOGGER.info( "Created " + cooker + "." ) ;

    final String echoMessage = "someMessage" ;

    final io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint >> echoCommand =
        createEchoCommand( cooker, echoMessage ) ;
    new Expectations() { {
      featureMock.echo( ENDPOINT, echoMessage ) ;
    } } ;

    echoCommand.callReceiver( featureMock ) ;

  }


  @Test
  public void addInterface() throws Exception {

    final CommandCooker< Endpoint, SomeFeature< Endpoint > > cooker = new CommandCooker<>(
        PREAMBLE_CODEC_FACTORY,
        FEATURE_REGISTRY,
        CODECS,
        COMMAND_NAME_PREFIX,
        featureMethod -> {
          final ImmutableSet< Class< ? > > classes = ImmutableSet.copyOf(
              featureMethod.commandAnnotation.moreInterfaces() ) ;
          if( classes.contains( Incrementer.class ) ) {
            return ImmutableMap.of(
                Incrementer.class,
                m -> "{ return int1 ; }"
            ) ;
          } else {
            return ImmutableMap.of() ;
          }
        },
        new Stamp.Generator( clock )
    ) ;
    LOGGER.info( "Created " + cooker + "." ) ;

    final List<
        io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint >>
    > commands = new ArrayList<>() ;
    final SomeFeature< Endpoint > featureProxy = cooker.createReceiver( commands::add ) ;
    LOGGER.info( "Created " + featureProxy + "." ) ;
    featureProxy.increment( ENDPOINT, 123 ) ;

    final io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint >> firstCommand =
        commands.get( 0 ) ;
    LOGGER.info( "Obtained " + firstCommand + "." ) ;

    assertThat( firstCommand ).isInstanceOf( Incrementer.class ) ;
    assertThat( ( ( Incrementer ) firstCommand ).delta() ).isEqualTo( 123 ) ;


  }

// =======
// Fixture
// =======

  private static final Logger LOGGER = LoggerFactory.getLogger( CommandCookerTest.class ) ;

  private static final String COMMAND_NAME_PREFIX =
      CommandCookerTest.class.getPackage().getName() + "_Command" ;

  private interface SomeFeature< ENDPOINT_SPECIFIC > {
    @AsCommand( persist = false )
    void echo( ENDPOINT_SPECIFIC endpointSpecific, String who ) ;

    @AsCommand( moreInterfaces = { Incrementer.class } )
    void increment( ENDPOINT_SPECIFIC endpointSpecific, int delta ) ;
  }

  private interface Incrementer {
    int delta() ;
  }

  private static class Endpoint extends StringWrapper< Endpoint >{
    public Endpoint( final String wrapped ) {
      super( wrapped ) ;
    }

    public String wrappedString() {
      return wrapped ;
    }
  }

  private static final Endpoint ENDPOINT = new Endpoint( "someEndpoint" ) ;

  private static final ImmutableMap< Type, Codec > CODECS = ImmutableMap.of(
      String.class,
      new Codec< String >() {
        @Override
        public String decodeFrom( final PositionalFieldReader positionalFieldReader ) throws IOException {
          return positionalFieldReader.readNullableString() ;
        }

        @Override
        public void encodeTo( final String s, final PositionalFieldWriter positionalFieldWriter ) throws IOException {
          positionalFieldWriter.writeNullableString( s ) ;
        }
      },
      Integer.TYPE,
      new Codec< Integer >() {
        @Override
        public Integer decodeFrom( final PositionalFieldReader positionalFieldReader ) throws IOException {
          return positionalFieldReader.readIntegerPrimitive() ;
        }

        @Override
        public void encodeTo( final Integer integer, final PositionalFieldWriter positionalFieldWriter )
            throws IOException
        {
          positionalFieldWriter.writeIntegerPrimitive( integer ); ;
        }
      }

  ) ;

  private static final FeatureRegistry FEATURE_REGISTRY = new FeatureRegistry(
      ImmutableList.of( SomeFeature.class ),
      CODECS.keySet()
  ) ;


  private static final Supplier< CommandCooker.PreambleCodec< Endpoint > > PREAMBLE_CODEC_FACTORY =
      () -> new CommandCooker.PreambleCodec< Endpoint >() {

        private String decodedCommandName = null ;
        private Endpoint decodedEndpoint = null ;

        @Override
        public void decode( final PositionalFieldReader positionalFieldReader ) throws IOException {
          decodedEndpoint = new Endpoint( positionalFieldReader.readDelimitedString() ) ;
          decodedCommandName = positionalFieldReader.readDelimitedString() ;
        }

        @Override
        public String commandName() {
          return decodedCommandName ;
        }

        @Override
        public Endpoint endpointSpecific() {
          return decodedEndpoint ;
        }

        private String commandNameToEncode = null ;
        private Endpoint endpointToEncode = null ;

        @Override
        public void set( final Endpoint endpoint, final String commandName ) {
          this.endpointToEncode = endpoint ;
          this.commandNameToEncode = commandName ;
        }

        @Override
        public void encode( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
          positionalFieldWriter.writeDelimitedString( endpointToEncode.wrappedString() ); ;
          positionalFieldWriter.writeDelimitedString( commandNameToEncode ); ;
        }
      }
  ;

  private static io.github.otcdlink.chiron.command.Command<
      Endpoint,
      SomeFeature< Endpoint >
  > createEchoCommand(
      final CommandCooker< Endpoint, SomeFeature< Endpoint > > cooker,
      final String echoMessage
  ) {
    final List<
        io.github.otcdlink.chiron.command.Command< Endpoint, SomeFeature< Endpoint > >
    > commands = new ArrayList<>() ;
    final SomeFeature< Endpoint > featureProxy = cooker.createReceiver( commands::add ) ;
    LOGGER.info( "Created " + featureProxy + "." ) ;

    featureProxy.echo( ENDPOINT, echoMessage ) ;
    assertThat( commands ).hasSize( 1 ) ;

    return commands.get( 0 ) ;
  }

  private final UpdateableClock clock = UpdateableClock.newClock( Stamp.FLOOR_MILLISECONDS ) ;
}