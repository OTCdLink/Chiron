package com.otcdlink.chiron.flow.journal;

import com.google.common.base.Charsets;
import com.google.common.collect.AbstractIterator;
import com.google.common.io.CharSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.otcdlink.chiron.codec.CommandBodyDecoder;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.codec.Decoder;
import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.designator.DesignatorForger;
import com.otcdlink.chiron.integration.echo.UpwardEchoCommand;
import com.otcdlink.chiron.middle.session.SessionIdentifier;
import com.otcdlink.chiron.toolbox.text.LineBreak;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class JournalReplayFixture {

  private final Logger logger ;
  public final File journalFile ;
  public final LineBreak lineBreak ;

  public final List< Command< Designator, ? > > commandRecorder = new ArrayList<>() ;

  public JournalReplayFixture(
      final Logger logger,
      final File journalFile,
      final LineBreak lineBreak
  ) {
    this.logger = checkNotNull( logger ) ;
    this.journalFile = checkNotNull( journalFile ) ;
    this.lineBreak = checkNotNull( lineBreak ) ;
  }

  public static UpwardEchoCommand< Designator > newCommand( final int counter ) {
    return new UpwardEchoCommand<>(
        DesignatorForger.newForger()
            .session( new SessionIdentifier( "7he5e5510n" ) )
            .flooredInstant( 0 )
            .counter( counter )
            .upward()
        ,
        "hello-" + counter
    ) ;
  }

  public UpwardEchoCommand< FileDesignatorCodecTools.DesignatorFromFile > command(
      final int index
  ) {
    return ( UpwardEchoCommand< FileDesignatorCodecTools.DesignatorFromFile > ) ( Object )
        commandRecorder.get( index ) ;
  }

  public void createFile( final int commandCount ) throws IOException {

    final Iterable< String > lines = () -> new AbstractIterator< String >() {
      int counter = 0 ;
      @Override
      protected String computeNext() {
        if( counter == 0 ) {
          counter ++ ;
          return "SchemaVersion 0 ApplicationVersion dev-SNAPSHOT " ;
        } else if( counter <= commandCount ){
          final String line = "0:" + counter + " 7he5e5510n echo hello-" + counter + " " ;
          counter ++ ;
          return line ;
        } else {
          return endOfData() ;
        }
      }
    } ;

    final CharSink charSink = Files.asCharSink(
        journalFile, Charsets.US_ASCII, FileWriteMode.APPEND ) ;
    charSink.writeLines( lines, lineBreak.asString ) ;

    logger.info( "Wrote " + journalFile.length() + " bytes into '" +
        journalFile.getAbsolutePath() + "'." ) ;

  }


  public static < DESIGNATOR, DUTY > void replayInto(
      final File intradayFile,
      final Decoder< DESIGNATOR > designatorDecoder,
      final CommandBodyDecoder< DESIGNATOR, DUTY > commandBodyDecoder,
      final Consumer< Command< DESIGNATOR, DUTY > > commandConsumer,
      final LineBreak lineBreak
  ) throws java.io.FileNotFoundException {
    final JournalFileReader< DESIGNATOR, DUTY > fileReader = new JournalFileReader<>(
        intradayFile,
        designatorDecoder,
        commandBodyDecoder,
        0,
        lineBreak
    ) ;
    Flux.fromIterable( fileReader.sliceIterable() )
        .handle( fileReader::decodeSlice )
        .doOnNext( commandConsumer )
        .blockLast()
    ;
  }


}
