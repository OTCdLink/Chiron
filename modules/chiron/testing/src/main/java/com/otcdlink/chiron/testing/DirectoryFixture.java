/*
 * Copyright (C) 2010 Laurent Caillette
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.otcdlink.chiron.testing;

import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.assertj.core.util.Strings;

import java.io.File;
import java.io.IOException;
//import org.novelang.logger.Logger;
//import org.novelang.logger.LoggerFactory;

/**
 * Creates directories on-demand for test purposes.
 * Each test is supposed to instantiate this class in the <code>setUp()</code> method because
 * test name (method name) is not available at the time Test constructor is called.
 * <p>
 * Formerly belonging to {@code org.novelang.testing.junit} package.
 */
public class DirectoryFixture {

//  private static final Logger LOGGER = LoggerFactory.getLogger( DirectoryFixture.class ) ;

  private final String testIdentifier ;


  public DirectoryFixture( final String testIdentifier ) {
    Preconditions.checkArgument( ! Strings.isNullOrEmpty( testIdentifier ) ) ;
    this.testIdentifier = testIdentifier ;
//    LOGGER.debug( "Created ", this ) ;

  }

  public String toString() {
    return getClass().getSimpleName() + "{" + testIdentifier + "}" ;
  }

  public static final String SCRATCH_DIRECTORY_SYSTEM_PROPERTY_NAME =
      "org.novelang.test.scratch.dir" ;
  public static final String DELETE_SCRATCH_DIRECTORY_SYSTEM_PROPERTY_NAME =
      "org.novelang.test.scratch.delete" ;

  public static final String DEFAULT_SCRATCH_DIR_NAME = "test-scratch" ;

  /**
   * Synchronize every access to static field on this.
   */
  private static final Object LOCK = new Object() ;

  /**
   * Static field holding the directory once defined.
   */
  @SuppressWarnings( { "StaticNonFinalField" } )
  private static File allFixturesDirectory = null ;

  private static File getAllFixturesDirectory() throws IOException {
    synchronized( LOCK ) {
      File file = allFixturesDirectory ;
      if( null == file ) {

        final String testfilesDirSystemProperty =
            System.getProperty( SCRATCH_DIRECTORY_SYSTEM_PROPERTY_NAME ) ;
        if( null == testfilesDirSystemProperty ) {
          file = new File( DEFAULT_SCRATCH_DIR_NAME ).getCanonicalFile() ;
        } else {
          file = new File( testfilesDirSystemProperty ).getCanonicalFile() ;
        }

        if(
            file.exists() &&
            ! "no".equalsIgnoreCase(
                System.getProperty( DELETE_SCRATCH_DIRECTORY_SYSTEM_PROPERTY_NAME ) )
            ) {
          MoreFiles.deleteRecursively( file.toPath(), RecursiveDeleteOption.ALLOW_INSECURE ) ;
        } else {
          if( file.mkdir() ) {
//            LOGGER.debug( "Created '", file.getAbsolutePath(), "'." ) ;
          }
        }
//        LOGGER.info( "Created ", file.getAbsolutePath(), "' as clean directory for all fixtures." ) ;
      }
      allFixturesDirectory = file ;
      return allFixturesDirectory ;
    }
  }

  public static final int TIMEOUT_SECONDS = 5 ;

  private File scratchDirectory;

  public File getDirectory() throws IOException {
    if( null == scratchDirectory) {
      scratchDirectory = new File( getAllFixturesDirectory(), testIdentifier ) ;
      if( scratchDirectory.exists() ) {
        MoreFiles.deleteRecursively(
            scratchDirectory.toPath(),
            RecursiveDeleteOption.ALLOW_INSECURE
        ) ;
      }
      if( scratchDirectory.mkdirs() ) {
//        LOGGER.debug( "Created '", scratchDirectory.getAbsolutePath(), "'." ) ;
      }
    }
    return scratchDirectory;
  }


}
