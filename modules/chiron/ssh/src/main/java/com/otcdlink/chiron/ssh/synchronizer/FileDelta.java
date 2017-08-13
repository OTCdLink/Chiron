package com.otcdlink.chiron.ssh.synchronizer;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Computes what should be uploaded and what should be deleted and which directories to
 * create remotely, basing on {@link FileKey} and
 * {@link FileDetail#LIKELINESS_COMPARATOR}.
 */
public class FileDelta {

  public final String remoteBaseDirectory ;
  public final ImmutableBiMap< FileKind, String > parentDirectoryByKind ;
  public final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles ;
  public final ImmutableSet< FileKey > remoteFilesToDelete ;
  public final ImmutableSet< FileKey > localFilesToUpload ;
  public final ImmutableSet< String > remoteDirectoriesToCreate ;

  public FileDelta(
      final String remoteBaseDirectory,
      final ImmutableBiMap< FileKind, String > parentDirectoryByKind,
      final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles,
      final ImmutableKeyHolderMap< FileKey, FileDetail > remoteFiles
  ) {
    this.remoteBaseDirectory = checkNotNull( remoteBaseDirectory ) ;
    this.parentDirectoryByKind = checkNotNull( parentDirectoryByKind ) ;

    this.localFiles = checkNotNull( localFiles ) ;

    final ImmutableSet.Builder< FileKey > deletionListBuilder = ImmutableSet.builder() ;
    final ImmutableSet.Builder< FileKey > uploadListBuilder = ImmutableSet.builder() ;
    final ImmutableSet.Builder< String > directoryListBuilder = ImmutableSet.builder() ;

    for( final FileDetail localFileDetail : localFiles.values() ) {
      final FileDetail remoteFileDetail = remoteFiles.get( localFileDetail.key ) ;
      if( remoteFileDetail == null ||
          FileDetail.LIKELINESS_COMPARATOR.compare( localFileDetail, remoteFileDetail ) != 0
      ) {
        uploadListBuilder.add( localFileDetail.key ) ;
      }
    }

    for( final FileDetail remoteFileDetail : remoteFiles.values() ) {
      final FileDetail localFileDetail = localFiles.get( remoteFileDetail.key ) ;
      if( localFileDetail == null ) {
        deletionListBuilder.add( remoteFileDetail.key ) ;
      }
    }

    directoryListBuilder.add( remoteBaseDirectory + '/' +
        parentDirectoryByKind.get( FileKind.PACKAGED ) ) ;

    for( final FileKind fileKind : FileKind.values() ) {
      if( fileKind != FileKind.PACKAGED ) {
        final Set< String > localDirectories  =
            relativeDirectories( localFiles.keySet(), fileKind ) ;
        final Set< String > remoteDirectories  =
            relativeDirectories( remoteFiles.keySet(), fileKind ) ;
        for( final String localDirectory : localDirectories ) {
          if( ! remoteDirectories.contains( localDirectory  ) ) {
            directoryListBuilder.add( remoteBaseDirectory + '/' +
                parentDirectoryByKind.get( fileKind ) + '/' + localDirectory ) ;
          }
        }
      }
    }

    localFilesToUpload = uploadListBuilder.build() ;
    remoteFilesToDelete = deletionListBuilder.build() ;
    remoteDirectoriesToCreate = directoryListBuilder.build() ;
  }

  private static ImmutableSet< String > relativeDirectories(
      final ImmutableSet< FileKey > fileKeys,
      final FileKind fileKind
  ) {
    final ImmutableSet.Builder< String > relativeDirectories = ImmutableSet.builder() ;
    fileKeys.stream()
        .filter( fileKey -> fileKey.kind == fileKind )
        .forEach( fileKey -> {
          final String element = extractParent( fileKey ) ;
          if( element != null ) {
            relativeDirectories.add( element ) ;
          }
        } )
    ;
    return relativeDirectories.build() ;
  }

  private static String relativeDirectoryNonPackaged( final FileKey fileKey ) {
    if( fileKey.kind != FileKind.PACKAGED ) {
      return extractParent( fileKey ) ;
    }
    return null ;
  }

  private static String extractParent( final FileKey fileKey ) {
    final int slashIndex = fileKey.relativePath.lastIndexOf( '/' ) ;
    if( slashIndex < 0 ) {
      return null ;
    } else {
      return fileKey.relativePath.substring( 0, slashIndex ) ;
    }
  }


}
