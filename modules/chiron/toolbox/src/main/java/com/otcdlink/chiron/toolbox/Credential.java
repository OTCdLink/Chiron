package com.otcdlink.chiron.toolbox;

import com.otcdlink.chiron.toolbox.text.TextTools;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a user name and its password.
 */
public final class Credential {

  private final String loginName ;

  private final String password ;

  public Credential( final String loginName, final String password ) {
    checkArgument( areValid( loginName, password ) ) ;

    this.loginName = loginName;
    this.password = password ;
  }

  private static boolean areValid( final String loginName, final String password ) {
    return  ! TextTools.isBlank( loginName ) && ! TextTools.isBlank( password ) ;
  }

  public static Credential maybeCreate( final String loginName, final String password ) {
    return areValid( loginName, password ) ? new Credential( loginName, password ) : null ;
  }

  /**
   * @return a non-null, non-empty {@code String}.
   */
  public String getLogin() {
    return loginName;
  }

  /**
   * @return a non-null, non-empty {@code String}.
   */
  public String getPassword() {
    return password ;
  }

/*
  @Override
  public void addHandshakeFieldsTo( final Map<String, Object> extField ) {
      extField.put( ChannelNames.MESSAGE_KEY_USER_LOGIN, getLogin() ) ;
      extField.put( ChannelNames.MESSAGE_KEY_USER_PASSWORD, getPassword() ) ;
  }
*/

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{"
        + loginName
//        + ";" + password
        + "}" ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final Credential that = ( Credential ) other;

    return COMPARATOR.compare( this, that ) == 0 ;
  }

  public static final Comparator< Credential > COMPARATOR = new ComparatorTools.WithNull<Credential>() {
    @Override
    protected int compareNoNulls( final Credential first, final Credential second ) {
      final int loginNameComparison =
          ComparatorTools.STRING_COMPARATOR.compare( first.loginName, second.loginName ) ;
      if( loginNameComparison == 0 ) {
        final int passwordComparison =
            ComparatorTools.STRING_COMPARATOR.compare( first.password, second.password ) ;
        return passwordComparison ;
      } else {
        return loginNameComparison ;
      }
    }
  } ;

  @Override
  public int hashCode() {
    final int result = loginName.hashCode() ;
    return result ;
  }
}
