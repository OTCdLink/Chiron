package com.otcdlink.chiron.toolbox.internet;

public class InternetNameFormatException extends Exception {

  public InternetNameFormatException( final String name ) {
    super( "Incorrect name: '" + name + "'" ) ;
  }
}
