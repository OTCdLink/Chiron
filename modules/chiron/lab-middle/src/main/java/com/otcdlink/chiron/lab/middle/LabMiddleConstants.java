package com.otcdlink.chiron.lab.middle;

import com.otcdlink.chiron.middle.tier.WebsocketFrameSizer;

public interface LabMiddleConstants {

  WebsocketFrameSizer WEBSOCKET_FRAME_SIZER = WebsocketFrameSizer.tightSizer( 8192 ) ;
}
