package com.otcdlink.chiron.fixture.tcp.http;

import com.otcdlink.chiron.fixture.tcp.TcpTransitServer;

/**
 * Base contract for an HTTP proxy that may add {@link #lag(int)} to transferred data.
 *
 * <h1>Reference documentation</h1>
 * <ul>
 *   <li>
 *     <a href="https://www.mnot.net/blog/2011/07/11/what_proxies_must_do" >What Proxies Must Do</a>
 *   </li><li>
 *     <a href="https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-14#section-9.9" >Via header field</a>
 *   </li>
 * </ul>
 */
public interface HttpProxy extends TcpTransitServer {

  /**
   * Adds lag to transferred data. Reducing the lag doesn't affect ordering of delivered messages.
   */
  void lag( final int lagMs ) ;
}
