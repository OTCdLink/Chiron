package io.github.otcdlink.chiron.ssh;

import io.github.otcdlink.chiron.toolbox.internet.Hostname;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

public interface SshService extends Closeable {

  Session newSession() throws ConnectionException, TransportException ;

  SCPFileTransfer newScpFileTransfer() ;

  Hostname remoteHost() ;

  LocalPortForwarder newLocalPortForwarder(
      LocalPortForwarder.Parameters parameters,
      ServerSocket serverSocket
  ) ;

  void close() throws IOException ;


  interface Factory {
    SshService createFor( final Hostname hostname ) throws Exception;
  }
}
