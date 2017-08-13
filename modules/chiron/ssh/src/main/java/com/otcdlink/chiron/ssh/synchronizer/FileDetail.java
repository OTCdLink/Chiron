package com.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Strings;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.collection.KeyHolder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The characteristics associated with a {@link FileKey}.
 */
public class FileDetail implements KeyHolder<FileKey > {

  public final FileKey key ;

  /**
   * Relative to a well-known directory, typically: {@code /} for local files,
   * {@code ~/piston} for remote ones.
   */
  public final String parentPath ;

  public final DateTime lastChange ;
  public final long size ;

  public FileDetail(
      final String parentPath,
      final String keyingPath,
      final FileKind kind,
      final DateTime lastChange,
      final long size
  ) {
    checkArgument( ! Strings.isNullOrEmpty( parentPath ), "Bad parentPath: " + parentPath ) ;
    this.parentPath = parentPath ;
    this.key = new FileKey( keyingPath, kind ) ;
    this.lastChange = lastChange ;
    this.size = size ;
  }


  @Override
  public FileKey key() {
    return key ;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
        "key=" + key + "; " +
        "parentPath='" + parentPath + "'; " +
        "lastChange=" + lastChange + "'; " +
        "size=" + size +
        '}'
    ;
  }

  public static final Comparator< FileDetail > LIKELINESS_COMPARATOR =
      new ComparatorTools.WithNull<FileDetail>() {
        @Override
        protected int compareNoNulls( final FileDetail first, final FileDetail second ) {
          final int sizeComparison =
              ComparatorTools.LONG_COMPARATOR.compare( first.size, second.size ) ;
          if( sizeComparison == 0 ) {
            final int lastModifiedComparison = DateTimeComparator.getInstance().compare(
                first.lastChange, second.lastChange ) ;
            return lastModifiedComparison ;
          } else {
            return sizeComparison ;
          }
        }
      }
  ;
}
