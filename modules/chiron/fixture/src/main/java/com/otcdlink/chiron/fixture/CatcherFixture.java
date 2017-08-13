package com.otcdlink.chiron.fixture;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.catcher.LoggingCatcher;
import org.assertj.core.description.Description;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.assertj.core.api.Assertions.assertThat;

public class CatcherFixture {

  public static RecordingCatcher< Record > newSimpleRecordingCatcher() {
    return new RecordingCatcher< Record >() {
      @Override
      public void processThrowable( final Throwable throwable ) {
        super.processThrowable( throwable ) ;
        addRecord( new Record( throwable ) ) ;
      }
    } ;
  }


  public static abstract class RecordingCatcher< RECORD extends Record >
      extends LoggingCatcher
  {
    private final List< RECORD > records = Collections.synchronizedList( new ArrayList<>() ) ;

    protected final void addRecord( final RECORD record ) {
      records.add( checkNotNull( record ) ) ;
    }

    public final ImmutableList< RECORD > records() {
      return ImmutableList.copyOf( records ) ;
    }

    public void assertEmpty() {
      final ImmutableList< RECORD > throwables = records() ;
      assertThat( throwables ).describedAs( new Description() {
        @Override
        public String value() {
          final StringWriter stringWriter = new StringWriter() ;
          final PrintWriter printWriter = new PrintWriter( stringWriter ) ;
          for( final Record record : throwables ) {
            record.throwable.printStackTrace( printWriter ) ;
            printWriter.print( "\n" ) ;
          }
          return stringWriter.toString() ;
        }
      } ).isEmpty() ;
    }

  }

  public static class Record {
    public final Throwable throwable ;

    public Record( final Throwable throwable ) {
      this.throwable = throwable ;
    }
  }
}
