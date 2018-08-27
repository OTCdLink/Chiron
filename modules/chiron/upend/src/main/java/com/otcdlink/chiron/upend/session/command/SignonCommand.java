package com.otcdlink.chiron.upend.session.command;

import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.middle.tier.CommandInterceptor;

/**
 * Tagging interface, useful when implementing a {@link CommandInterceptor} which should
 * let pass all signon-related {@link Command}s.
 */
public interface SignonCommand { }
