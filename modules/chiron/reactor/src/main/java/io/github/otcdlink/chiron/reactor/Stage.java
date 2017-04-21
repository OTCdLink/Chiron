package io.github.otcdlink.chiron.reactor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import reactor.fn.Consumer;
import reactor.fn.Function;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class Stage implements LifecycleEnabled {

  private final String name ;

  private final AtomicReference< State > state = new AtomicReference<>() ;

  @SuppressWarnings( "ThisEscapedInObjectConstruction" )
  protected Stage() {
    this.name = ToStringTools.nameAndCompactHash( this ) ;
  }

  protected Stage( final String name ) {
    checkArgument( ! Strings.isNullOrEmpty( name ) ) ;
    this.name = name ;
  }

  @Override
  public void start() throws Exception { }

  @Override
  public void stop(
      final long timeout,
      final TimeUnit timeUnit
  ) throws Exception { }



  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + '{' + toStringBody() + '}' ;
  }

  protected String toStringBody() {
    return "" ;
  }

  enum State {
    STOPPED, STARTING, RUNNING, STOPPING, CRASHED, ;
  }

  public interface Transformer< COMMAND > extends
      LifecycleEnabled,
      Function< COMMAND, COMMAND >
  { }

  public interface Spreader< COMMAND > extends
      LifecycleEnabled,
      Function< COMMAND, ImmutableList< COMMAND > >
  {
    @Override
    ImmutableList< COMMAND > apply( COMMAND command ) ;

  }

  public interface Absorber< COMMAND > extends
      LifecycleEnabled,
      Consumer< COMMAND >
  { }

  public interface Charger< COMMAND > extends LifecycleEnabled { }

}
