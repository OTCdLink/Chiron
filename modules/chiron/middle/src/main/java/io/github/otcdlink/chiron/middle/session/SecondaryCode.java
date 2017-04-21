package io.github.otcdlink.chiron.middle.session;

import com.google.common.base.Strings;
import io.github.otcdlink.chiron.toolbox.Credential;
import io.github.otcdlink.chiron.toolbox.StringWrapper;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a Secondary Code.
 *
 * @see Credential
 */
public class SecondaryCode extends StringWrapper< SecondaryCode > {

  public SecondaryCode( final String s ) {
    super( s ) ;
    checkArgument( ! Strings.isNullOrEmpty( s ) ) ;
  }

  public static SecondaryCode create( final String s ) {
    return s == null ? null : new SecondaryCode( s ) ;
  }

  public static final Comparator< SecondaryCode > COMPARATOR = new WrapperComparator<>() ;

  public String asString() {
    return wrapped ;
  }
}
