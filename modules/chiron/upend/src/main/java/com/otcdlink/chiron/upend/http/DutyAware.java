package com.otcdlink.chiron.upend.http;

import com.otcdlink.chiron.toolbox.handover.Handover;

/**
 * A {@link RenderableCommand} can implement this interface so it can call
 * some methods of {@link DUTY}, relying on {@link Handover} to obtain results.
 */
public interface DutyAware< DUTY > {
  void callDuty( DUTY duty ) ;
}
