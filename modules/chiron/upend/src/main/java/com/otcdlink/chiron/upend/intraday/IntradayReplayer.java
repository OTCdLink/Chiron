package com.otcdlink.chiron.upend.intraday;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.toolbox.clock.Clock;

import java.io.File;
import java.io.IOException;

public interface IntradayReplayer< DESIGNATOR, DUTY > {

  boolean searchForRecoveryFile() throws IOException ;

  void replayLogFile() throws Exception ;

  void renameRecoveryFile() ;

//  File resolveRecoveryFile() throws IOException ;

  interface Factory< DESIGNATOR, DUTY > {
    IntradayReplayer< DESIGNATOR, DUTY > create(
        final Clock clock,
        final File originalFile,
        final CommandConsumer< Command< DESIGNATOR, DUTY > > commandConsumer
    ) ;
    
  }
}
