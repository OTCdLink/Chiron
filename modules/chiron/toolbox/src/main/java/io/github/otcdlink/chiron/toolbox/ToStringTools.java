package io.github.otcdlink.chiron.toolbox;

public final class ToStringTools {

  private ToStringTools() { }

  public static String getNiceClassName( final Object object ) {
    final Class originClass = object.getClass() ;
    return getNiceName( originClass ) ;
  }

  public static String getNiceName( final Class originClass ) {
    String className ;
    if( originClass.isAnonymousClass() ) {
      className = originClass.getName().substring( originClass.getName().lastIndexOf( '.' ) + 1 ) ;
      return className + '(' + originClass.getSuperclass().getSimpleName() + ')' ;
    } else {
      className = originClass.getSimpleName() ;
      Class enclosingClass = originClass.getEnclosingClass() ;
      while( enclosingClass != null ) {
        className = enclosingClass.getSimpleName() + "$" + className ;
        enclosingClass = enclosingClass.getEnclosingClass() ;
      }
      if( originClass.isAnonymousClass() ) {
        className += "$(" + originClass.getSuperclass().getSimpleName() + ")" ;
      }
      return className ;
    }
  }

  public static Object createLockWithNiceToString( final Class<?> someClass ) {
    return new Object() {
      @Override
      public String toString() {
        return getNiceName( someClass ) + "#lock{" +
            Integer.toHexString( System.identityHashCode( this ) ) + "}" ;
      }
    } ;
  }

  public static String nameAndCompactHash( final Object object ) {
    if( object == null ) {
      return null ;
    } else {
      return getNiceClassName( object ) + compactHashForNonNull( object ) ;
    }
  }

  public static String nameAndHash( final Object object ) {
    if( object == null ) {
      return null ;
    } else {
      return getNiceClassName( object ) + "@" + System.identityHashCode( object ) ;
    }
  }

  public static String compactHashForNonNull( final Object object ) {
    return '@' + Integer.toHexString( System.identityHashCode( object ) ) ;
  }


  public static String enumToString( final Enum enumItem ) {
    return enumItem.getClass().getSimpleName() + '{' + enumItem.name() + '}' ;
  }

  public static void appendIfNotNull(
      final StringBuilder stringBuilder,
      final String name,
      final Object value
  ) {
    if( value != null ) {
      stringBuilder.append( ';' ).append( name ).append( '=' ).append( value ) ;
    }
  }
}
