package com.otcdlink.chiron.middle.shaft;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Instantiates {@link Command} objects and then runs {@link Command#callReceiver(Object)}.
 */
public class CrafterShaft< ENDPOINT_SPECIFIC, DUTY > implements MethodCallShaft< DUTY > {

  private static final Logger LOGGER = LoggerFactory.getLogger( CrafterShaft.class ) ;

  private final Function<
      CommandConsumer< Command< ENDPOINT_SPECIFIC, DUTY > >,
      DUTY
  > commandCrafterFactory ;

  public CrafterShaft(
      final Function<
          CommandConsumer< Command< ENDPOINT_SPECIFIC, DUTY > >,
          DUTY
      > commandCrafterFactory,
      final ENDPOINT_SPECIFIC endpointSpecific
  ) {
    this.commandCrafterFactory = checkNotNull( commandCrafterFactory ) ;
  }

  @Override
  public void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier
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
    final MethodCallRecorder< DUTY > methodCallRecorder = new MethodCallRecorder<>(
        methodCaller,
        methodCallCollector::add
    ) ;
    for( final Command command : recordedCommands ) {
      command.callReceiver( methodCallRecorder.recordingDuty() ) ;
    }

    final ImmutableList< MethodCall > recordedMethodCalls = methodCallCollector.build() ;
    LOGGER.info( "Recorded " + recordedMethodCalls.size() + " method calls so far." ) ;

    final ImmutableList< MethodCall > expectedMethodCalls =
        MethodCallRecorder.recordMethodCalls( methodCaller ) ;

    MethodCallVerifier.verifyAll( expectedMethodCalls, recordedMethodCalls, methodCallVerifier ) ;

  }

}
