package io.github.otcdlink.chiron.upend.intraday;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.codec.CommandBodyDecoder;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.command.CommandConsumer;
import io.github.otcdlink.chiron.command.codec.Codec;
import io.github.otcdlink.chiron.fixture.CatcherFixture;
import io.github.otcdlink.chiron.middle.shaft.MethodCall;
import io.github.otcdlink.chiron.middle.shaft.MethodCallRecorder;
import io.github.otcdlink.chiron.middle.shaft.MethodCallShaft;
import io.github.otcdlink.chiron.middle.shaft.MethodCallVerifier;
import io.github.otcdlink.chiron.middle.shaft.MethodCaller;
import io.github.otcdlink.chiron.toolbox.clock.Clock;
import io.github.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class IntradayFileShaft< DESIGNATOR, DUTY > implements MethodCallShaft< DUTY > {

  private static final Logger LOGGER = LoggerFactory.getLogger( IntradayFileShaft.class ) ;

  private final Clock clock ;
  private final File directory ;
  private final Codec< DESIGNATOR > designatorCodec ;
  private final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder ;
  private final Function<
      CommandConsumer< Command< DESIGNATOR, DUTY > >,
      DUTY
  > commandCrafterFactory ;
  private final LineBreak lineBreak ;

  public IntradayFileShaft(
      final Clock clock,
      final File directory,
      final Function< CommandConsumer< Command< DESIGNATOR, DUTY > >, DUTY > commandCrafterFactory,
      final Codec< DESIGNATOR > designatorCodec,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final LineBreak lineBreak
  ) {
    this.clock = checkNotNull( clock ) ;
    checkArgument( directory.exists() ) ;
    checkArgument( directory.isDirectory() ) ;
    this.directory = directory ;
    this.commandCrafterFactory = checkNotNull( commandCrafterFactory ) ;
    this.designatorCodec = checkNotNull( designatorCodec ) ;
    this.commandBodyDecoder = checkNotNull( commandBodyDecoder ) ;
    this.lineBreak = checkNotNull( lineBreak ) ;
  }

  @Override
  public void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier
  ) throws Exception {
    final File intradayFile = new File( directory, "my.intraday" ) ;
    final CatcherFixture.RecordingCatcher recordingCatcher =
        CatcherFixture.newSimpleRecordingCatcher() ;
    final IntradayFileChannelPersister< DESIGNATOR, DUTY > intradayFileChannelPersister =
        new IntradayFileChannelPersister<>(
            intradayFile,
            designatorCodec,
            0,
            "dev-SNAPSHOT"
        )
    ;
    intradayFileChannelPersister.open() ;
    try {
      final DUTY duty = commandCrafterFactory.apply( intradayFileChannelPersister ) ;
      methodCaller.callMethods( duty ) ;
    } finally {
      intradayFileChannelPersister.close() ;
    }

    final ImmutableList.Builder< MethodCall > methodCallCollector = ImmutableList.builder() ;
    final MethodCallRecorder< DUTY > methodCallRecorder = new MethodCallRecorder<>(
        methodCaller,
        methodCallCollector::add
    ) ;

    final IntradayFileReplayer< DESIGNATOR, DUTY > fileReplayer = new IntradayFileReplayer<>(
        clock,
        intradayFile,
        lineBreak,
        designatorCodec,
        commandBodyDecoder,
        command -> command.callReceiver( methodCallRecorder.recordingDuty() ),
        0
    ) ;

    fileReplayer.searchForRecoveryFile() ;
    fileReplayer.replayLogFile() ;

    final ImmutableList< MethodCall > recordedMethodCalls = methodCallCollector.build() ;
    LOGGER.info( "Recorded " + recordedMethodCalls.size() + " method calls so far." ) ;

    final ImmutableList< MethodCall > expectedMethodCalls =
        MethodCallRecorder.recordMethodCalls( methodCaller ) ;

    MethodCallVerifier.verifyAll( expectedMethodCalls, recordedMethodCalls, methodCallVerifier ) ;


  }
}
