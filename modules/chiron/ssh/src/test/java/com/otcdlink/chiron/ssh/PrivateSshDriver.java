package com.otcdlink.chiron.ssh;

import com.google.common.collect.ImmutableList;
import com.otcdlink.chiron.toolbox.internet.Hostname;
import com.otcdlink.chiron.toolbox.security.KeystoreTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PrivateSshDriver extends SshDriver< SshDriver.Setup > {

  private static final Logger LOGGER = LoggerFactory.getLogger( PrivateSshDriver.class ) ;

  static {
    KeystoreTools.activateJavaCryptographyExtensions() ;
  }

  public PrivateSshDriver() throws Exception {
    super( LOGGER, newSshService() ) ;
  }

  public static SshService newSshService() throws Exception {
    return SshTools.newSshServiceFactoryWithAuthenticationProxy(
        "otcdlink",
        SshTools.HOSTKEYVERIFIERS_YES
    ).createFor( Hostname.parse( "192.168.100.110" ) ) ;
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
