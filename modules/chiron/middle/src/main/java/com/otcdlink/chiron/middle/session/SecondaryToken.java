package com.otcdlink.chiron.middle.session;

import com.google.common.base.Strings;
import com.otcdlink.chiron.toolbox.Credential;
import com.otcdlink.chiron.toolbox.StringWrapper;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * For strong-typing the token that {@code SecondaryAuthenticator} sends, waiting a
 * {@link SecondaryCode} in return.
 *
 * @see Credential
 */
public class SecondaryToken extends StringWrapper< SecondaryToken >  {

  public SecondaryToken( final String s ) {
    super( s ) ;
    checkArgument( !Strings.isNullOrEmpty( s ) ) ;
  }

  public static SecondaryToken create( final String s ) {
    return s == null ? null : new SecondaryToken( s ) ;
  }

  public String asString() {
    return wrapped ;
  }

  public static final Comparator< SecondaryToken > COMPARATOR = new WrapperComparator<>() ;

}
