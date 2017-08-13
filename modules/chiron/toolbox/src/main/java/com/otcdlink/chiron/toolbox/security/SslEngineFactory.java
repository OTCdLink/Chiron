package com.otcdlink.chiron.toolbox.security;

import javax.net.ssl.SSLEngine;

/**
 * Creates an {@link SSLEngine}, as needed by {@code io.netty.handler.ssl.SslHandler}.
 * This class has the same role as Netty's {@code io.netty.handler.ssl.SslHandler}, which
 * supports a lot of features we don't need, like delegation to OpenSSL, or
 * <a href="https://en.wikipedia.org/wiki/Application-Layer_Protocol_Negotiation">Application-Layer Protocol Negotiation</a>.
 * In addition, {@code io.netty.handler.ssl.SslHandler} expects the {@code java.security.KeyStore}
 * to be in a file, while we prefer an URL stream (when loaded from a jar resource), or
 * the Keystore instance itself ({@link Autosigner} can skip file creation).
 */
public interface SslEngineFactory {

  SSLEngine newSslEngine() ;

  interface ForClient extends SslEngineFactory { }

  interface ForServer extends SslEngineFactory { }

}
