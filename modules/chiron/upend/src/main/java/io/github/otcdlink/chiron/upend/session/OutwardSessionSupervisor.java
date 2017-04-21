package io.github.otcdlink.chiron.upend.session;

/**
 * Convenient interface aggregation.
 */
public interface OutwardSessionSupervisor< CHANNEL, ADDRESS >
    extends
    SessionSupervisor< CHANNEL, ADDRESS >,
    SignonOutwardDuty
{ }
