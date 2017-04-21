package io.github.otcdlink.chiron.command;

import java.util.function.Consumer;

public interface CommandConsumer< COMMAND extends Command > extends Consumer< COMMAND > {

  void accept( COMMAND command ) ;
}
