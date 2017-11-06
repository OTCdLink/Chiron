package com.otcdlink.chiron.ssh.synchronizer;

import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileDeltaTest {

  @Test
  public void allEmpty() throws Exception {
    final FileDelta fileDelta = newFileDelta( NO_FILES, NO_FILES ) ;
    assertThat( fileDelta.localFiles ).isEmpty() ;
    assertThat( fileDelta.localFilesToUpload ).isEmpty() ;
    assertThat( fileDelta.remoteFilesToDelete ).isEmpty() ;
  }

  @Test
  public void allSame() throws Exception {
    final FileDelta fileDelta = newFileDelta( LOCALS_A11, REMOTES_A11 ) ;
    assertThat( fileDelta.localFiles ).isEqualTo( LOCALS_A11 ) ;
    assertThat( fileDelta.localFilesToUpload ).isEmpty() ;
    assertThat( fileDelta.remoteFilesToDelete ).isEmpty() ;
  }

  @Test
  public void missingRemote() throws Exception {
    final FileDelta fileDelta = newFileDelta( LOCALS_A11, NO_FILES ) ;
    assertThat( fileDelta.localFiles ).isEqualTo( LOCALS_A11 ) ;
    assertThat( fileDelta.localFilesToUpload ).containsOnly( LOCAL_A_1_1.key ) ;
    assertThat( fileDelta.remoteFilesToDelete ).isEmpty() ;
    assertThat( fileDelta.remoteDirectoriesToCreate ).containsOnly(
        "rider/packaged",
        "rider/compiled/my"
    ) ;
  }

  @Test
  public void detailDifferenceCausingUpload() throws Exception {
    final ImmutableKeyHolderMap<FileKey, FileDetail> localFiles = LOCALS_A11;
    final ImmutableKeyHolderMap<FileKey, FileDetail> remoteFiles = REMOTES_A21;
    final FileDelta fileDelta = newFileDelta( localFiles, remoteFiles ) ;
    assertThat( fileDelta.localFiles ).isEqualTo( LOCALS_A11 ) ;
    assertThat( fileDelta.localFilesToUpload ).containsOnly( LOCAL_A_1_1.key ) ;
    assertThat( fileDelta.remoteFilesToDelete ).isEmpty() ;
  }

  @Test
  public void surplusCausingDeletion() throws Exception {
    final FileDelta fileDelta = newFileDelta( LOCALS_A11, REMOTES_A11_B11 ) ;
    assertThat( fileDelta.localFiles ).isEqualTo( LOCALS_A11 ) ;
    assertThat( fileDelta.localFilesToUpload ).isEmpty() ;
    assertThat( fileDelta.remoteFilesToDelete ).contains( REMOTE_B_1_1.key ) ;
  }

// =======
// Fixture
// =======

  private static final DateTime DATETIME_1 = new DateTime( 2011, 1, 1, 1, 1, 1, DateTimeZone.UTC ) ;
  private static final DateTime DATETIME_2 = new DateTime( 2022, 2, 2, 2, 2, 2, DateTimeZone.UTC ) ;

  private static final ImmutableKeyHolderMap< FileKey, FileDetail > NO_FILES =
      ImmutableKeyHolderMap.of() ;

  public static final FileDetail REMOTE_A_1_1 = new FileDetail(
      "rider/compiled", "my/ClassA", FileKind.COMPILED, DATETIME_1, 111 ) ;

  public static final FileDetail REMOTE_A_2_1 = new FileDetail(
      "rider/compiled", "my/ClassA", FileKind.COMPILED, DATETIME_2, 111 ) ;

  public static final FileDetail REMOTE_B_1_1 = new FileDetail(
      "rider/compiled", "my/ClassB", FileKind.COMPILED, DATETIME_1, 111 ) ;

  public static final FileDetail LOCAL_A_1_1 = new FileDetail(
      "/user/project/module/m1", "my/ClassA", FileKind.COMPILED, DATETIME_1, 111 ) ;

  private static final ImmutableKeyHolderMap< FileKey, FileDetail > LOCALS_A11 =
      ImmutableKeyHolderMap.of(
          LOCAL_A_1_1
      ) 
  ;

  private static final ImmutableKeyHolderMap< FileKey, FileDetail > REMOTES_A11 =
      ImmutableKeyHolderMap.of(
          REMOTE_A_1_1
      )
  ;

  private static final ImmutableKeyHolderMap< FileKey, FileDetail > REMOTES_A21 =
      ImmutableKeyHolderMap.of(
          REMOTE_A_2_1
      )
  ;

  private static final ImmutableKeyHolderMap< FileKey, FileDetail > REMOTES_A11_B11 =
      ImmutableKeyHolderMap.of(
          REMOTE_A_1_1,
          REMOTE_B_1_1
      )
  ;

  private static FileDelta newFileDelta(
      final ImmutableKeyHolderMap< FileKey, FileDetail > localFiles,
      final ImmutableKeyHolderMap< FileKey, FileDetail > remoteFiles
  ) {
    return new FileDelta(
        "rider",
        FileKind.shortDirectoryNameMap(),
        localFiles,
        remoteFiles
    ) ;
  }

}