package com.otcdlink.chiron.upend.session;

/**
 * Convenient interface aggregation.
 */
public interface OutwardSessionSupervisor< CHANNEL, ADDRESS, SESSION_PRIMER >
    extends
    SessionSupervisor< CHANNEL, ADDRESS, SESSION_PRIMER >,
    SignonOutwardDuty< SESSION_PRIMER >
{ }
