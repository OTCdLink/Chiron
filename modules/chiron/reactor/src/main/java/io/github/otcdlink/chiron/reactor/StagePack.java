package io.github.otcdlink.chiron.reactor;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.github.otcdlink.chiron.toolbox.catcher.Catcher;

import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * All the {@link Stage}s.
 */
public abstract class StagePack< COMMAND > {

  public final ReactiveAwareCatcher< COMMAND > catcher ;

  public final BiFunction< Exception, COMMAND, COMMAND > errorTranslator ;

  public final ImmutableSet< LifecycleEnabled > stratums ;

  public final ImmutableSet< LifecycleEnabled > stratumsNoHttp ;

  public final ImmutableSet< LifecycleEnabled > stratumsHttpOnly ;

  private final Stage.Charger< COMMAND > httpUpward ;

  private final Stage.Absorber< COMMAND > httpDownward ;

  private final Stage.Spreader< COMMAND > logic ;

  private final Stage.Spreader< COMMAND > persister ;

  private final Stage.Absorber< COMMAND > sessionSupervisor ;

  private final Stage.Transformer< COMMAND > passwordHasher ;

  /**
   * May be {@code null}.
   */
  private final Stage.Absorber< COMMAND > emailSender ;

  private final Stage.Transformer< COMMAND > throttler ;

  protected StagePack(
      final Catcher catcher,
      final BiFunction< Exception, COMMAND, COMMAND > errorTranslator,
      final Stage.Charger< COMMAND > httpUpward,
      final Stage.Absorber< COMMAND > httpDownward,
      final Stage.Spreader< COMMAND > logic,
      final Stage.Spreader< COMMAND > persister,
      final Stage.Transformer< COMMAND > passwordHasher,
      final Stage.Absorber< COMMAND > emailSender,
      final Stage.Transformer< COMMAND > throttler,
      final Stage.Absorber< COMMAND > sessionSupervisor
  ) {
    this.catcher = ReactiveAwareCatcher.wrap( catcher ) ;
    this.errorTranslator = checkNotNull( errorTranslator ) ;
    final ImmutableSet.Builder< LifecycleEnabled > builder = ImmutableSet.builder() ;
    add( builder, this.httpUpward = checkNotNull( httpUpward ) ) ;
    add( builder, this.httpDownward = checkNotNull( httpDownward ) ) ;
    add( builder, this.logic = checkNotNull( logic ) ) ;
    add( builder, this.persister = checkNotNull( persister ) ) ;
    add( builder, this.passwordHasher = checkNotNull( passwordHasher ) ) ;
    add( builder, this.emailSender = emailSender ) ;
    add( builder, this.throttler = checkNotNull( throttler ) ) ;
    add( builder, this.sessionSupervisor = checkNotNull( sessionSupervisor ) ) ;
    this.stratums = builder.build() ;

    final Predicate< LifecycleEnabled > httpStratumPredicate =
        s -> s != httpUpward && s != httpDownward ;

    this.stratumsNoHttp = ImmutableSet.copyOf(
        Sets.filter( stratums, httpStratumPredicate::test ) ) ;

    this.stratumsHttpOnly = ImmutableSet.copyOf(
        Sets.filter( stratums, httpStratumPredicate.negate()::test ) ) ;
  }

  private static < ITEM > void add( final ImmutableSet.Builder< ITEM > builder, final ITEM item ) {
    if( item != null ) {
      builder.add( item ) ;
    }
  }

  public Stage.Charger< COMMAND > httpUpward() {
    return httpUpward ;
  }

  public Stage.Absorber< COMMAND > httpDownward() {
    return httpDownward ; 
  }

  public Stage.Spreader< COMMAND > logic() {
    return logic ;
  }

  public Stage.Spreader< COMMAND > persister() {
    return persister ;
  }

  public Stage.Transformer< COMMAND > passwordHasher() {
    return passwordHasher ;
  }

  public Stage.Absorber< COMMAND > emailSender() {
    return emailSender ;
  }

  public Stage.Absorber< COMMAND > sessionSupervisor() {
    return sessionSupervisor ;
  }

  public Stage.Transformer< COMMAND > throttler() {
    return throttler ;
  }

  public abstract boolean isInternalPasswordStuff( COMMAND command ) ;

  public abstract boolean isInternalPasswordTransformation( COMMAND command ) ;

  public abstract boolean isInternalEmailStuff( COMMAND command ) ;

  public abstract boolean isInternalThrottlingDuration( COMMAND command ) ;

  public abstract boolean isInternalThrottlerDelay( COMMAND command ) ;

  public abstract boolean isInternalSessionStuff( COMMAND command ) ;

  public boolean isDownwardCommand( final COMMAND command ) {
    return
        ! isInternalPasswordStuff( command ) &&
        ! isInternalEmailStuff( command ) &&
        ! isInternalThrottlingDuration( command ) &&
        ! isInternalThrottlerDelay( command ) &&
        ! isInternalSessionStuff( command )
    ;
  }

  public abstract boolean isFailureCommand( final COMMAND command ) ;
}
