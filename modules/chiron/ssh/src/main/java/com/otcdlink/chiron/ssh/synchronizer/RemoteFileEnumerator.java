package com.otcdlink.chiron.ssh.synchronizer;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableBiMap;
import com.otcdlink.chiron.ssh.SshTools;
import com.otcdlink.chiron.toolbox.FilenameTools;
import com.otcdlink.chiron.toolbox.collection.ImmutableKeyHolderMap;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Enumerates remote files.
 * Assumes that files of each {@link FileKind} are in separate directories.
 */
public class RemoteFileEnumerator {

  private static final Logger LOGGER = LoggerFactory.getLogger( RemoteFileEnumerator.class ) ;

  public final String baseDirectory ;
  public final ImmutableBiMap< FileKind, String > parentDirectoryByKind ;

  public RemoteFileEnumerator(
      final String baseDirectory
  ) {
    this(
        baseDirectory,
        FileKind.shortDirectoryNameMap()
    ) ;
  }
  public RemoteFileEnumerator(
      final String baseDirectory,
      final ImmutableBiMap< FileKind, String > parentDirectoryByKind
  ) {
    this.baseDirectory = FilenameTools.removeTrailingSlash( checkNotNull( baseDirectory ) ) ;
    this.parentDirectoryByKind = checkNotNull( parentDirectoryByKind ) ;
  }

  public ImmutableKeyHolderMap< FileKey, FileDetail > enumerate( final Session freshSession )
      throws IOException
  {
    final Session.Command command = freshSession.exec(
        createCommand() ) ;
    final ImmutableKeyHolderMap< FileKey, FileDetail > fileDetails =
        enumerate( command.getInputStream() ) ;
    command.join() ;
    LOGGER.info( "File retrieval terminated with exit status " + command.getExitStatus() + "." ) ;
    return fileDetails ;
  }

  protected String createCommand() {
    return UNIX_FIND_COMMAND + baseDirectory + UNIX_FIND_ARGUMENTS;
  }

  public ImmutableKeyHolderMap< FileKey, FileDetail > enumerate( final InputStream inputStream )
      throws IOException
  {
    return enumerate( new BufferedReader( new InputStreamReader( inputStream ) ) ) ;
  }

  public ImmutableKeyHolderMap< FileKey, FileDetail > enumerate( final BufferedReader reader )
      throws IOException
  {
    final ImmutableKeyHolderMap.Builder< FileKey, FileDetail > fileMapBuilder =
        ImmutableKeyHolderMap.builder() ;

    while( true ) {
      final String line = reader.readLine() ;
      if( line == null ) {
        break ;
      }
      try {
        // LOGGER.debug( "Retrieved line '" + line + "'" ) ;
        final FileDetail fileDetail = parse( line ) ;
        if( fileDetail != null ) {
          fileMapBuilder.put( fileDetail ) ;
        }
      } catch( final RuntimeException e ) {
        LOGGER.warn( "Could not parse '" + line + "': " + e.getClass().getName() +
            " saying " + e.getMessage() + "." ) ;
      }
    }

    return fileMapBuilder.build() ;
  }

  private FileDetail parse( final String line ) {
    final Matcher matcher = FILE_PATTERN.matcher( line ) ;
    if( matcher.find() ) {
      final String fileCompletePath = matcher.group( 8 ) ;
      for( final Map.Entry< FileKind, String > entry : parentDirectoryByKind.entrySet() ) {
        final String baseDirectoryWithKind =
            FilenameTools.removeTrailingSlash( baseDirectory + "/" + entry.getValue() ) ;
        if( fileCompletePath.length() > baseDirectoryWithKind.length() &&
            fileCompletePath.startsWith( baseDirectoryWithKind )
        ) {
          final int year = Integer.parseInt( matcher.group( 1 ) ) ;
          final int month = Integer.parseInt( matcher.group( 2 ) ) ;
          final int dayInMonth = Integer.parseInt( matcher.group( 3 ) ) ;
          final int hourInDay = Integer.parseInt( matcher.group( 4 ) ) ;
          final int minuteInHour = Integer.parseInt( matcher.group( 5 ) ) ;
          final int secondInMinute = Integer.parseInt( matcher.group( 6 ) ) ;
          final int fileSize = Integer.parseInt( matcher.group( 7 ) ) ;
          final DateTime lastModified = new DateTime(
              year, month, dayInMonth, hourInDay, minuteInHour, secondInMinute, DateTimeZone.UTC ) ;
          return new FileDetail(
              FilenameTools.removeTrailingSlash( baseDirectoryWithKind ),
              fileCompletePath.substring( baseDirectoryWithKind.length() + 1 ),
              entry.getKey(),
              lastModified,
              fileSize
          ) ;
        }
      }
    }
    return null ;
  }

  private static final String UNIX_FIND_COMMAND = "export TZ=UTC ; /usr/bin/find " ;

  /**
   * Give things like:
   * <pre>
2014-11-26 13:17:29.8587466930 UTC 9955149 /tmp/OTCdLink-installer-1132428576686505399.7z
2014-11-20 19:51:05.6125617090 UTC 32252861 /tmp/OTCdLink-installer-8602944473808919621.7z
</pre>
   */
  private static final String UNIX_FIND_ARGUMENTS =
      " -type f -printf '%TY-%Tm-%Td %TT %TZ %s %p\\n'" ;

  private static final Pattern FILE_PATTERN = Pattern.compile(
      "([0-9]{4})-([0-9]{2})-([0-9]{2}) ([012][0-9]):([0-9]{2}):([0-9]{2})(?:\\.[0-9]+) UTC " +
          "([0-9]+) (.+)"
  ) ;


  public static void main( final String... arguments ) throws Exception {
    try( SSHClient sshClient = SshTools.createDemoSshClient( "otcdlink", "trader.otcdlink.com" ) ) {
      final ImmutableKeyHolderMap< FileKey, FileDetail > enumerate = new RemoteFileEnumerator(
          "rider",
          FileKind.shortDirectoryNameMap()
      ).enumerate( sshClient.startSession() ) ;
      LOGGER.info( "Got:\n  " + Joiner.on( "\n  " ).join( enumerate.values() ) ) ;
    }
  }
}
