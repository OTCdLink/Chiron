package com.otcdlink.chiron.flow.journal;


import com.google.common.base.Charsets;
import com.otcdlink.chiron.toolbox.text.LineBreak;

import java.nio.charset.Charset;

public interface IntradayPersistenceConstants {

  Charset CHARSET = Charsets.US_ASCII ;

  int VERSION = 0 ;

  String MAGIC = "SchemaVersion" ;


  /**
   * Multiplatform-friendly so we can read files from one platform on another one.
   */
  LineBreak LINE_BREAK = LineBreak.CR_UNIX ;


  String RECOVERY_FILE_SUFFIX = "recovering" ;
  String RECOVERED_FILE_SUFFIX = "intraday-recovered" ;

}
