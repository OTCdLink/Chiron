package com.otcdlink.chiron.ssh.synchronizer;

import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.collection.KeyHolder;

import java.util.Comparator;

/**
 * The name to match local and remote files.
 */
public class FileKey implements KeyHolder.Key< FileKey > {

  public final String relativePath ;
  public final FileKind kind ;

  public FileKey( final String relativePath, final FileKind kind ) {
    this.relativePath = relativePath ;
    this.kind = kind ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' +
        "relativePath='" + relativePath + "'; " +
        "kind=" + kind +
        '}'
    ;
  }

  @Override
  public int compareTo( final FileKey other ) {
    return COMPARATOR.compare( this, other ) ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final FileKey that = ( FileKey ) other ;

    return COMPARATOR.compare( this, that ) == 0 ;
  }

  @Override
  public int hashCode() {
    int result = relativePath.hashCode() ;
    result = 31 * result + kind.hashCode();
    return result ;
  }

  public static final Comparator< FileKey > COMPARATOR = new ComparatorTools.WithNull< FileKey >() {
    @Override
    protected int compareNoNulls( final FileKey first, final FileKey second ) {
      final int kindComparison = first.kind.compareTo( second.kind ) ;
      if( kindComparison == 0 ) {
        final int relativePathComparison = first.relativePath.compareTo( second.relativePath ) ;
        return relativePathComparison ;
      } else {
        return kindComparison ;
      }
    }
  } ;
}
