package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.designator.Designator;
import com.otcdlink.chiron.integration.echo.EchoCodecFixture;
import com.otcdlink.chiron.integration.echo.EchoUpwardDuty;

import java.io.File;

public class JournalFileReaderTest
    extends AbstractJournalFileReaderTest<
        JournalFileReader< Designator, EchoUpwardDuty< Designator > >
    >
{

  protected JournalFileReader< Designator, EchoUpwardDuty< Designator > >
  newJournalReader( final File file ) throws java.io.FileNotFoundException {
    return new JournalFileReader<>(
        file,
        new FileDesignatorCodecTools.InwardDesignatorDecoder(),
        new EchoCodecFixture.PartialUpendDecoder(),
        0,
        true
    ) ;
  }


}