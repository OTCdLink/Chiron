package io.github.otcdlink.chiron.lab.middle;

import io.github.otcdlink.chiron.middle.tier.WebsocketFrameSizer;

public interface LabMiddleConstants {

  WebsocketFrameSizer WEBSOCKET_FRAME_SIZER = WebsocketFrameSizer.tightSizer( 8192 ) ;
}
