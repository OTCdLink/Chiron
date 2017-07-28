package io.github.otcdlink.chiron.ssh;

import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.security.KeystoreTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrivateSshJavaDriver extends SshJavaDriver< SshJavaDriver.Setup > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PrivateSshJavaDriver.class ) ;

  static {
    KeystoreTools.activateJavaCryptographyExtensions() ;
  }

  public PrivateSshJavaDriver() throws Exception {
    super( LOGGER, PrivateSshDriver.newSshService() ) ;
  }


  private final List< String > stdoutLines = Collections.synchronizedList( new ArrayList<>() ) ;
  private final List< String > stderrLines = Collections.synchronizedList( new ArrayList<>() ) ;

  public final ImmutableList< String > stdoutLines() {
    return ImmutableList.copyOf( stdoutLines ) ;
  }

  public final ImmutableList< String > stderrLines() {
    return ImmutableList.copyOf( stderrLines ) ;
  }

  @Override
  protected void onStdoutLine( String line ) {
    super.onStdoutLine( line ) ;
    stdoutLines.add( line ) ;
  }

  @Override
  protected void onStderrLine( String line ) {
    super.onStderrLine( line ) ;
    stderrLines.add( line ) ;
  }

}
