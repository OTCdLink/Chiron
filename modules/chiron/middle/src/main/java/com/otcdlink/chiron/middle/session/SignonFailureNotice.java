package com.otcdlink.chiron.middle.session;


import com.otcdlink.chiron.middle.TypedNotice;

import java.util.Comparator;

public class SignonFailureNotice extends TypedNotice< SignonFailure > {
  
  public SignonFailureNotice( final SignonFailure signonFailure ) {
    super( signonFailure ) ;
  }

  public SignonFailureNotice( final SignonFailure signonFailure, final String message ) {
    super( signonFailure, message ) ;
  }

  public static final Comparator< SignonFailureNotice > COMPARATOR = TypedNotice.comparator() ;


}
