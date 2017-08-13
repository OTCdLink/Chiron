package com.otcdlink.chiron.middle;

import com.otcdlink.chiron.toolbox.StringWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * A phone number that enforces
 * <a href="http://www.twilio.com/help/faq/phone-numbers/how-do-i-format-phone-numbers-to-work-internationally" >E164</a>
 * format (plus spaces) as required by Twilio.
 */
public final class PhoneNumber extends StringWrapper< PhoneNumber > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PhoneNumber.class ) ;

  public PhoneNumber( final String dialableSequence ) {
    super( dialableSequence ) ;
    if( ! PATTERN.matcher( dialableSequence ).matches() ) {
      throw new PhoneNumberFormatException( dialableSequence ) ;
    }
  }

  public String getAsString() {
    return super.getWrappedString() ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + getAsString() + "}" ;
  }

  public static final Pattern PATTERN = Pattern.compile( "\\+?(?: *\\d+)+" ) ;
  static {
    LOGGER.debug( "Crafted regex " + PATTERN.pattern() ) ;
  }

  public static final Comparator< PhoneNumber > COMPARATOR
      = new WrapperComparator< PhoneNumber >() ;

}
