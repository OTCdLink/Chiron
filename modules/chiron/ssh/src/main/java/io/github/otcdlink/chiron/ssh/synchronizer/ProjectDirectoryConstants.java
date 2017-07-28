package io.github.otcdlink.chiron.ssh.synchronizer;

import com.google.common.collect.ImmutableSet;
import io.github.otcdlink.chiron.toolbox.SafeSystemProperty;

import java.io.File;

/**
 * Convenient values for Chiron developers running tests.
 * Those values include hardcoded paths, and dependencies to other projects developed along
 * with Chiron.
 */
public interface ProjectDirectoryConstants {

  File DEFAULT_PROJECT_HOME = new File(
      SafeSystemProperty.Standard.USER_HOME.value, "/Projects/OTCdLink" ) ;

  String MAVEN_MODULES_DIRECTORY_NAME = "maven-modules" ;

  ImmutableSet< File > MODULES_DIRECTORY = ImmutableSet.of(
      new File( DEFAULT_PROJECT_HOME, MAVEN_MODULES_DIRECTORY_NAME + "/chiron" ),
      new File( DEFAULT_PROJECT_HOME, MAVEN_MODULES_DIRECTORY_NAME + "/trader" )
  ) ;
}
