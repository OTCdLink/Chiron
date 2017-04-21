package io.github.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.buffer.BytebufCoat;
import io.github.otcdlink.chiron.buffer.BytebufTools;
import io.github.otcdlink.chiron.codec.CommandBodyDecoder;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class CodecShaft< ENDPOINT_SPECIFIC, DUTY > implements MethodCallShaft< DUTY > {

  private static final Logger LOGGER = LoggerFactory.getLogger( CodecShaft.class ) ;

  private final Function<
      CommandConsumer< Command< ENDPOINT_SPECIFIC, DUTY > >,
      DUTY
  > commandCrafterFactory ;

  private final CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY > partialCommandDecoder ;
  private final ENDPOINT_SPECIFIC endpointSpecific ;

  public CodecShaft(
      final Function<
          CommandConsumer< Command< ENDPOINT_SPECIFIC, DUTY > >,
          DUTY
      > commandCrafterFactory,
      final CommandBodyDecoder< ENDPOINT_SPECIFIC, DUTY > partialCommandDecoder,
      final ENDPOINT_SPECIFIC endpointSpecific
  ) {
    this.commandCrafterFactory = checkNotNull( commandCrafterFactory ) ;
    this.partialCommandDecoder = checkNotNull( partialCommandDecoder ) ;
    this.endpointSpecific = checkNotNull( endpointSpecific ) ;
  }

  private final PooledByteBufAllocator byteBufAllocator = new PooledByteBufAllocator( false ) ;
  private final BytebufTools.Coating coating = BytebufTools.threadLocalRecyclableCoating() ;


  @Override
  public void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier
  ) throws Exception {
    submit( methodCaller, methodCallVerifier, null ) ;
  }

  public void submit(
      final MethodCaller< DUTY > methodCaller,
      final CommandFailureVerifier commandFailureVerifier
  ) throws Exception {
    submit( methodCaller, null, commandFailureVerifier ) ;
  }

  private void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier,
      final CommandFailureVerifier commandFailureVerifier
  ) {
    final ImmutableList.Builder< Command< ENDPOINT_SPECIFIC, DUTY > > commandCollector =
        ImmutableList.builder() ;
    final DUTY duty = commandCrafterFactory.apply( commandCollector::add ) ;
    methodCaller.callMethods( duty ) ;
    final ImmutableList< Command< ENDPOINT_SPECIFIC, DUTY > > recordedCommands =
        commandCollector.build() ;
    LOGGER.info( "Recorded " + recordedCommands.size() + " " + Command.class.getSimpleName() +
        " so far." ) ;

    final ImmutableList.Builder< MethodCall > methodCallCollector = ImmutableList.builder() ;

    final ImmutableList.Builder< CommandExecutionFailure > commandFailureCollector =
        ImmutableList.builder() ;

    final MethodCallRecorder< DUTY > methodCallRecorder = new MethodCallRecorder<>(
        methodCaller,
        methodCallCollector::add
    ) ;
    for( final Command command : recordedCommands ) {
      final ByteBuf byteBuf = byteBufAllocator.heapBuffer() ;
      final BytebufCoat bytebufCoat = coating.coat( byteBuf ) ;
      Command< ENDPOINT_SPECIFIC, DUTY > decodedCommand = null ;
      try {
        command.encodeBody( bytebufCoat ) ;
        decodedCommand = partialCommandDecoder.decodeBody(
            endpointSpecific,
            command.description().name(),
            bytebufCoat
        ) ;
        if( decodedCommand == null ) {
          throw new RuntimeException( "No " + Command.class.getSimpleName() + " with name '" +
              command.description().name() + "' in " + partialCommandDecoder ) ;
        }
        decodedCommand.callReceiver( methodCallRecorder.recordingDuty() ) ;
      } catch( final Exception e ) {
        if( commandFailureCollector != null && decodedCommand != null ) {
          commandFailureCollector.add( new CommandExecutionFailure( decodedCommand, e ) ) ;
        } else {
          final int readerIndex = byteBuf.readerIndex() ;
          byteBuf.resetReaderIndex() ;
          LOGGER.error(
              "Could not encode/decode " + command + " " +
              "(problem at reader index " + readerIndex + "): \n" +
              ByteBufUtil.prettyHexDump( byteBuf )
          ) ;
          throw new RuntimeException( e ) ;
        }
      } finally {
        coating.recycle() ;
        byteBuf.release() ;
      }
    }

    final ImmutableList< MethodCall > recordedMethodCalls = methodCallCollector.build() ;

    final ImmutableList< CommandExecutionFailure > commandExecutionFailures =
        commandFailureCollector.build() ;

    LOGGER.info(
        "Recorded " + recordedMethodCalls.size() + " successful method call" +
        ( recordedMethodCalls.size() <= 1 ? "" : "s" ) + " so far" +
        ", and " + commandExecutionFailures.size() + " failure" +
        ( commandExecutionFailures.size() <= 1 ? "" : "s" ) + "."
    ) ;

    final ImmutableList< MethodCall > expectedMethodCalls =
        MethodCallRecorder.recordMethodCalls( methodCaller ) ;

    if( commandExecutionFailures.isEmpty() ) {
      MethodCallVerifier.verifyAll( expectedMethodCalls, recordedMethodCalls, methodCallVerifier ) ;
    } else {
      if( recordedMethodCalls.isEmpty() ) {
        commandFailureVerifier.verify( commandExecutionFailures ) ;
      } else {
        LOGGER.error( "Recorded " + recordedMethodCalls + " and " + commandExecutionFailures +
            "." ) ;
        throw new IllegalStateException(
            "Mixing successful and failed method calls is suspicious, giving up" ) ;
      }
    }


  }

  /**
   * Useful as {@link CodecShaft#endpointSpecific}.
   */
  public static final class Uninvolved {
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{}" ;
    }

    public static final Uninvolved INSTANCE = new Uninvolved() ;
  }
}
