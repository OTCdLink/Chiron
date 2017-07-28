package io.github.otcdlink.chiron.ssh;

import com.google.common.collect.ImmutableSet;
import com.jcraft.jsch.agentproxy.AgentProxy;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.Identity;
import com.jcraft.jsch.agentproxy.sshj.AuthAgent;
import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.SecurityUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SshTools {

  private static final Logger LOGGER = LoggerFactory.getLogger( SshTools.class ) ;
  public static final long BIG_TIMEOUT_MS = TimeUnit.HOURS.toMillis( 2 ) ;

  private SshTools() { }

  @Deprecated
  public static SshService newSshServiceFromProtectedRsaKey(
      final String user,
      final String hostname,
      final PasswordFinder passwordFinder,
      final ImmutableSet< String > hostKeyVerifiers
  ) throws Exception {
    return newSshServiceFromProtectedRsaKey(
        user, hostname, hostKeyVerifiers( hostKeyVerifiers ), passwordFinder ) ;
  }

  /**
   * Asks for passhprase interactively in the console.
   */
  public static SshService newSshServiceFromProtectedRsaKey(
      final String user,
      final String hostname,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers,
      final PasswordFinder passwordFinder
  ) throws Exception {
    final SSHClient sshClient = newSshFromProtectedRsaKey(
        user, hostname, hostKeyVerifiers, passwordFinder ) ;
//    sshClient.getConnection().setWindowSize( 4096 ) ;
//    sshClient.getConnection().setTimeoutMs( ( int ) BIG_TIMEOUT_MS ) ;
    return asSshService( sshClient ) ;
  }

  public static SshService asSshService( final SSHClient sshClient ) {
    return new SshService() {
      @Override
      public Session newSession() throws ConnectionException, TransportException {
        return sshClient.startSession() ;
      }

      @Override
      public SCPFileTransfer newScpFileTransfer() {
        return sshClient.newSCPFileTransfer() ;
      }

      @Override
      public LocalPortForwarder newLocalPortForwarder(
          final LocalPortForwarder.Parameters parameters,
          final ServerSocket serverSocket
      ) {
        return sshClient.newLocalPortForwarder( parameters, serverSocket ) ;
      }

      @Override
      public void close() throws IOException {
        sshClient.close() ;
      }

      @Override
      public Hostname remoteHost() {
        return Hostname.parse( sshClient.getRemoteHostname() ) ;
      }
    } ;
  }

  public static ImmutableSet< HostKeyVerifier > hostKeyVerifiers(
      final String... hostFingerprints
  ) {
    return hostKeyVerifiers( ImmutableSet.copyOf( hostFingerprints ) ) ;
  }

  public static ImmutableSet< HostKeyVerifier > hostKeyVerifiers(
      final ImmutableSet< String > hostFingerprints
  ) {
    final ImmutableSet.Builder< HostKeyVerifier > hostKeyVerifiers = ImmutableSet.builder() ;
    for( final String hostFingerprint : hostFingerprints ) {
      hostKeyVerifiers.add( hostKeyVerifier( hostFingerprint ) ) ;
    }
    return hostKeyVerifiers.build() ;
  }

  public static HostKeyVerifier hostKeyVerifier( final String hostFingerprint ) {
    return new HostKeyVerifier() {
      @Override
      public boolean verify( final String hostname, final int port, final PublicKey publicKey ) {
        return SecurityUtils.getFingerprint( publicKey ).equals( hostFingerprint ) ;
      }
      @Override
      public String toString() {
        return HostKeyVerifier.class.getSimpleName() + '{' + hostFingerprint + '}' ;
      }
    } ;
  }

  public static SshService.Factory newSshServiceFactory(
      final String user,
      final PasswordFinder passwordFinder,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers
  ) throws Exception {
    return hostname -> asSshService( newSshFromProtectedRsaKey(
        user, hostname.asString(), hostKeyVerifiers, passwordFinder ) ) ;
  }

  public static SshService.Factory newSshServiceFactoryWithAuthenticationProxy(
      final String user,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers
  ) throws Exception {
    return hostname -> asSshService( newSshWithAuthenticationProxy(
        hostname.asString(), user, hostKeyVerifiers ) ) ;
  }

  /**
   * TODO: use SSH Agent Proxy.
   * https://github.com/ymnk/jsch-agent-proxy/blob/master/examples/src/main/java/com/jcraft/jsch/agentproxy/examples/SshjWithAgentProxy.java
   * https://repo1.maven.org/maven2/com/jcraft/jsch.agentproxy.sshj/0.0.9/jsch.agentproxy.sshj-0.0.9.pom
   */
  public static SSHClient newSshFromProtectedRsaKey(
      final String user,
      final String hostname,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers,
      final PasswordFinder passwordFinder
  ) throws Exception {
    return newSshFromProtectedRsaKey(
        user,
        hostname,
        hostKeyVerifiers,
        System.getProperty( "user.home" ) + "/.ssh/id_rsa",
        passwordFinder
    ) ;
  }

  public static SSHClient newSshFromProtectedRsaKey(
      final String user,
      final String hostname,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers,
      final String keyLocation,
      final PasswordFinder passwordFinder
  ) throws Exception {
    final SSHClient ssh = new SSHClient() ;
    hostKeyVerifiers.forEach( ssh::addHostKeyVerifier ) ;
    //ssh.useCompression() ;
    final KeyProvider keyProvider = ssh.loadKeys(
        keyLocation,
        passwordFinder
    ) ;
    ssh.loadKnownHosts() ;
    ssh.connect( hostname ) ;
    ssh.authPublickey( user, keyProvider ) ;
    return ssh ;
  }

  public static SSHClient newSshFromUnprotectedRsaKey(
      final String hostname,
      final String user,
      final String keyLocation
  ) throws Exception {
    final SSHClient ssh = new SSHClient() ;
    ssh.addHostKeyVerifier( HOSTKEYVERIFIER_YES ) ;
    final KeyProvider keyProvider = ssh.loadKeys( keyLocation ) ;
    ssh.connect( hostname ) ;
    ssh.authPublickey( user, keyProvider ) ;

    LOGGER.info( "Created " + SSHClient.class.getSimpleName() + " for " +
        user + '@' + hostname + " with key file '" + keyLocation + "'." ) ;
    return ssh ;
  }

  public static SSHClient newSshWithAuthenticationProxy(
      final String hostname,
      final String user,
      final ImmutableSet< HostKeyVerifier > hostKeyVerifiers
  ) throws Exception {
    final AgentProxy agentProxy = getAgentProxy() ;

    // https://stackoverflow.com/a/30953527/1923328
//    final DefaultConfig defaultConfig = new DefaultConfig() ;
//    defaultConfig.setKeepAliveProvider( KeepAliveProvider.KEEP_ALIVE ) ;
//    final SSHClient ssh = new SSHClient( defaultConfig ) ;
    final SSHClient ssh = new SSHClient() ;
    ssh.loadKnownHosts() ;
    hostKeyVerifiers.forEach( ssh::addHostKeyVerifier ) ;
    ssh.connect( hostname ) ;
    ssh.getConnection().getKeepAlive().setKeepAliveInterval( 30 ) ;  // Doesn't seem to help.
    ssh.auth( user, getAuthMethods( agentProxy ) ) ;

    LOGGER.info( "Created " + SSHClient.class.getSimpleName() + " for " +
        user + '@' + hostname + " with " + AgentProxy.class.getSimpleName() + "." ) ;
    return ssh ;
  }

  public static final HostKeyVerifier HOSTKEYVERIFIER_YES = new HostKeyVerifier() {
    @Override
    public boolean verify( final String hostname, final int port, final PublicKey key ) {
      return true ;
    }
    @Override
    public String toString() {
      return HostKeyVerifier.class.getSimpleName() + "{YES}" ;
    }
  } ;

  public static final ImmutableSet< HostKeyVerifier > HOSTKEYVERIFIERS_YES =
      ImmutableSet.of( HOSTKEYVERIFIER_YES ) ;


  @Deprecated
  public static SSHClient createDemoSshClient(
      final String user,
      final String hostname
  ) throws Exception {
    return createDemoSshClient( user, hostname, new CommandLinePasswordFinder() ) ;
  }

  public static SSHClient createDemoSshClient(
      final String user,
      final String hostname,
      final PasswordFinder passwordFinder
  ) throws Exception {
    return newSshFromProtectedRsaKey( user, hostname, SshTools.HOSTKEYVERIFIERS_YES, passwordFinder ) ;
  }


  public static class CommandLinePasswordFinder implements PasswordFinder {
    private static final String NO_PASSWORD = "" ;

    private String last = NO_PASSWORD ;

    @Override
    public char[] reqPassword( final Resource< ? > resource ) {
      if( ! last.isEmpty() ) {
        return last.toCharArray() ;
      }
      System.out.println( "Password for " + resource + ":" ) ;
      LOGGER.info( "Requesting password on the console ..." ) ;

      try(
          final InputStreamReader inputStreamReader = new InputStreamReader( System.in ) ;
          final BufferedReader lineReader = new BufferedReader( inputStreamReader, 40 )
      ) {
        last = lineReader.readLine() ;
        return last.toCharArray() ;
      } catch( final IOException e ) {
        throw new RuntimeException( e ) ;
      }
    }

    @Override
    public boolean shouldRetry( final Resource< ? > resource ) {
      last = NO_PASSWORD ;
      return true ;
    }
  }


  private static AgentProxy getAgentProxy() {
    final Connector connector = getAgentConnector() ;
    if( connector != null )
      return new AgentProxy( connector ) ;
    return null;
  }

  private static Connector getAgentConnector() {
    try {
      return ConnectorFactory.getDefault().createConnector() ;
    } catch( final AgentProxyException e ) {
      LOGGER.error( "Could not create " + AgentProxy.class.getSimpleName() + ".", e ) ;
    }
    return null ;
  }


  private static List< AuthMethod > getAuthMethods( final AgentProxy agent ) throws Exception {
    final Identity[] identities = agent.getIdentities() ;
    final List< AuthMethod > result = new ArrayList<>() ;
    for( final Identity identity : identities ) {
      result.add( new AuthAgent( agent, identity ) ) ;
    }
    return result ;
  }

}
