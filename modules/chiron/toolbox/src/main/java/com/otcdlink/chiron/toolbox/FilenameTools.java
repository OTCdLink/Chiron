package com.otcdlink.chiron.toolbox;

import com.google.common.base.Preconditions;

import java.util.regex.Pattern;

public final class FilenameTools {

  private FilenameTools() { }

  private static final Pattern FILENAME_SANITIZER_FORBIDDEN_PATTERN =
      Pattern.compile( "[^A-Za-z0-9\\-_,@.]" ) ;

  private static final Pattern FILENAME_SANITIZER_DOUBLEDOT_PATTERN =
      Pattern.compile( "\\.\\." ) ;

  public static String removeTrailingSlash( final String path ) {
    if( path == null ) {
      return null ;
    } else {
      return path.endsWith( "/" ) ? path.substring( 0, path.length() - 1 ) : path ;
    }
  }

  public static String sanitize( final String string ) {
    String sanitized = string ;
    sanitized = FILENAME_SANITIZER_FORBIDDEN_PATTERN.matcher( sanitized ).replaceAll( "" ) ;
    sanitized = FILENAME_SANITIZER_DOUBLEDOT_PATTERN.matcher( sanitized ).replaceAll( "." ) ;
    return sanitized ;
  }

  public static String removeSuffix( final String name, final String suffix ) {
    Preconditions.checkArgument( name.endsWith( suffix ) ) ;
    return name.substring( 0, name.length() - suffix.length() ) ;
  }

  /**
   * Gets the name minus the path from a full filename.
   * <p>
   * This method will handle a file in either Unix or Windows format.
   * The text after the last forward or backslash is returned.
   * <pre>
   * a/b/c.txt --> c.txt
   * a.txt     --> a.txt
   * a/b/c     --> c
   * a/b/c/    --> ""
   * </pre>
   * <p>
   * The output will be the same irrespective of the machine that the code is running on.
   * <p>
   * Copied from {org.apache.commons.io.FilenameUtils#getName(java.lang.String)}
   * version 2.4.
   *
   * @param filename  the filename to query, null returns null
   * @return the name of the file without the path, or an empty string if none exists
   */
  public static String getName( final String filename ) {
    if( filename == null ) {
      return null ;
    }
    final int index = indexOfLastSeparator( filename ) ;
    return filename.substring( index + 1 ) ;
  }

  /**
   * Returns the index of the last directory separator character.
   * <p>
   * This method will handle a file in either Unix or Windows format.
   * The position of the last forward or backslash is returned.
   * <p>
   * The output will be the same irrespective of the machine that the code is running on.
   * <p>
   * Copied from {org.apache.commons.io.FilenameUtils#indexOfLastSeparator(java.lang.String)}
   * version 2.4.
   *
   * @param filename  the filename to find the last path separator in, null returns -1
   * @return the index of the last separator character, or -1 if there
   * is no such character
   */
  private static int indexOfLastSeparator( final String filename ) {
    if( filename == null ) {
      return -1 ;
    }
    final int lastUnixPosition = filename.lastIndexOf( UNIX_SEPARATOR ) ;
    final int lastWindowsPosition = filename.lastIndexOf( WINDOWS_SEPARATOR ) ;
    return Math.max( lastUnixPosition, lastWindowsPosition ) ;
  }


  /**
   * The Unix separator character.
   * Copied from {@code org.apache.commons.io.FilenameUtils#UNIX_SEPARATOR}.
   */
  private static final char UNIX_SEPARATOR = '/';

  /**
   * The Windows separator character.
   * Copied from {@code org.apache.commons.io.FilenameUtils#WINDOWS_SEPARATOR}.
   */
  private static final char WINDOWS_SEPARATOR = '\\';


}
