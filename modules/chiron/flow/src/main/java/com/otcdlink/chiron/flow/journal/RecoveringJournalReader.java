package com.otcdlink.chiron.flow.journal;


import java.io.IOException;

public interface RecoveringJournalReader< COMMAND  > extends JournalReader< COMMAND > {

  /**
   * Must be called before calling {@link #sliceIterable()}, so a file to recover from
   * can be found. The {@link #sliceIterable()} will use the resolved file .
   */
  boolean resolveRecoveryFile() throws IOException ;

  void renameRecoveryFileToRecovered() ;

}
