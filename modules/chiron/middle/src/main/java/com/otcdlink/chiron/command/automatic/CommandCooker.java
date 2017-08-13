package com.otcdlink.chiron.command.automatic;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.PositionalFieldReader;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.CommandConsumer;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.command.codec.Codec;
import com.otcdlink.chiron.toolbox.ToStringTools;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import java.io.DataInput;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Resolves {@link Command} objects from Java code through "Feature" interfaces, with methods
 * annotated with {@link AsCommand}.
 * This is only for the latest version as defined by interfaces in use, the backward compatibility
 * comes from other stuff.
 */
public class CommandCooker<
    ENDPOINT_SPECIFIC,
    CALLABLE_RECEIVER
>
    implements Codec< Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > >
{
  private final Supplier< PreambleCodec< ENDPOINT_SPECIFIC > > preambleCodecFactory;
  private final FeatureRegistry featureRegistry ;
  private final ImmutableMap< Type, Codec > methodParametersCodecs ;
  private final CommandClassEnhancer commandClassEnhancer ;

  /**
   * Something like {@code foo.bar.MyCommand}.
   */
  private final String commandNamePrefix ;

  private final Stamp.Generator identifierGenerator ;

  /**
   * @param featureRegistry describes how to create {@link Command} objects.
   * @param methodParametersCodecs {@link Codec}s for parameters of interface's methods.
   * @param identifierGenerator
   */
  public CommandCooker(
      final Supplier< PreambleCodec< ENDPOINT_SPECIFIC > > preambleCodecFactory,
      final FeatureRegistry featureRegistry,
      final ImmutableMap< Type, Codec > methodParametersCodecs,
      final String commandNamePrefix,
      final CommandClassEnhancer commandClassEnhancer,
      final Stamp.Generator identifierGenerator
  ) {
    this.preambleCodecFactory = checkNotNull( preambleCodecFactory ) ;
    this.featureRegistry = checkNotNull( featureRegistry ) ;
    this.methodParametersCodecs = checkNotNull( methodParametersCodecs ) ;
    checkArgument( Pattern.matches(
        "[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)*", commandNamePrefix ) ) ;
    this.commandNamePrefix = commandNamePrefix ;
    this.commandClassEnhancer = commandClassEnhancer ;
    this.identifierGenerator = checkNotNull( identifierGenerator ) ;
  }

  @Override
  public void encodeTo(
      final Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > command,
      final PositionalFieldWriter positionalFieldWriter
  ) throws IOException {
    encode( preambleCodec(), command, positionalFieldWriter ) ;
  }

  @Override
  public final Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > decodeFrom(
      final PositionalFieldReader positionalFieldReader
  ) throws IOException {
    return decode( preambleCodec(), positionalFieldReader ) ;
  }

  @SuppressWarnings( "ThreadLocalNotStaticFinal" )
  private final ThreadLocal< PreambleCodec< ENDPOINT_SPECIFIC > > preambleCodecThreadLocal =
      new ThreadLocal<>() ;

  private PreambleCodec< ENDPOINT_SPECIFIC > preambleCodec() {
    final PreambleCodec< ENDPOINT_SPECIFIC > preambleCodec = preambleCodecThreadLocal.get() ;
    if( preambleCodec == null ) {
      final PreambleCodec< ENDPOINT_SPECIFIC > newInstance = preambleCodecFactory.get() ;
      preambleCodecThreadLocal.set( newInstance ) ;
      return newInstance ;
    } else {
      return preambleCodec ;
    }
  }

  protected void encode(
      final PreambleEncoder< ENDPOINT_SPECIFIC > preambleEncoder,
      final Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > command,
      final PositionalFieldWriter positionalFieldWriter
  ) throws IOException {
    preambleEncoder.set( command.endpointSpecific, command.description().name() ) ;
    preambleEncoder.encode( positionalFieldWriter ) ;
    command.encodeBody( positionalFieldWriter ) ;
  }

  protected Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > decode(
      final PreambleDecoder< ENDPOINT_SPECIFIC > preambleDecoder,
      final PositionalFieldReader positionalFieldReader
  ) throws IOException {
    preambleDecoder.decode( positionalFieldReader ) ;
    final String commandName = preambleDecoder.commandName() ;
    final ENDPOINT_SPECIFIC endpointSpecific = preambleDecoder.endpointSpecific() ;


    final FeatureRegistry.FeatureMethod featureMethod =
        featureRegistry.featureMethods.get( commandName ) ;
    final ImmutableMap.Builder< String, Object > argumentMapBuilder = ImmutableMap.builder() ;
    final FeatureRegistry.FeatureMethod.ArgumentIterator iterator =
        featureMethod.argumentIterator() ;
    while( iterator.hasNext() ) {
      final Type argumentType = iterator.next() ;
      final int argumentIndex = iterator.index() ;
      final Codec codec = methodParametersCodecs.get( argumentType ) ;
      final String fieldName = argumentName( argumentType, argumentIndex ) ;
      final Object value = codec.decodeFrom( positionalFieldReader ) ;
      argumentMapBuilder.put( fieldName, value ) ;
    }
    return new DynamicCommand<>(
        identifierGenerator.generate(),
        endpointSpecific,
        new CommandDescription( featureMethod ),
        methodParametersCodecs,
        featureMethod,
        argumentMapBuilder.build()
    ) ;
  }

  private final ClassPool ctPool = new ClassPool( true ) ;

  @SuppressWarnings( "unchecked" )
  public final CALLABLE_RECEIVER createReceiver(
      final CommandConsumer< Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > > commandConsumer
  ) {
    final Class[] interfaces = featureRegistry.featureClasses.toArray(
        new Class[ featureRegistry.featureClasses.size() ] ) ;

    return ( CALLABLE_RECEIVER ) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        interfaces,
        ( proxy, method, arguments ) -> {
          if( CommandCookerTools.callingObject$equals( method, arguments ) ) {
            return proxy == arguments[ 0 ] ;
          } else if( CommandCookerTools.callingObject$hashCode( method, arguments ) ) {
            return System.identityHashCode( proxy ) ;
          } else if( CommandCookerTools.callingObject$toString( method, arguments ) ) {
            final ImmutableList< Class< ? > > list = featureRegistry.featureClasses ;
            return CommandCooker.class.getSimpleName() + ".$$DynamicReceiver" +
                ToStringTools.compactHashForNonNull( proxy ) +
                '{' + CommandCookerTools.classNames( list ) + '}'
            ;
          } else {
            final FeatureRegistry.FeatureMethod featureMethod =
                featureRegistry.featureMethod( method ) ;
            checkArgument( arguments.length == featureMethod.specificArgumentTypes.size() + 1 ) ;

            ImmutableMap.Builder< String, Object > builder = ImmutableMap.builder() ;
            final FeatureRegistry.FeatureMethod.ArgumentIterator argumentIterator =
                featureMethod.argumentIterator() ;
            while( argumentIterator.hasNext() ) {
              final Type argumentType = argumentIterator.next() ;
              final int argumentIndex = argumentIterator.index() ;
              final Object argument = arguments[ argumentIndex ];
              builder.put( argumentName( argumentType, argumentIndex ), argument ) ;
            }

            final Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > dynamicCommand =
                createCommand( featureMethod, ( ENDPOINT_SPECIFIC ) arguments[ 0 ], builder.build() ) ;
            ;
            commandConsumer.accept( dynamicCommand ) ;
          }
          return null ;
        }
    ) ;
  }

  private Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > createCommand(
      final FeatureRegistry.FeatureMethod featureMethod,
      final ENDPOINT_SPECIFIC endpointSpecific,
      final ImmutableMap< String, Object > argumentMap
  ) throws NotFoundException, CannotCompileException {
    final Class< Command > commandClass = createCommandClass( featureMethod ) ;
//    return commandClass.getConstructor(  )
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  private Class< Command > createCommandClass(
      final FeatureRegistry.FeatureMethod featureMethod
  ) throws NotFoundException, CannotCompileException {
    final CtClass ctBase = ctPool.get( DynamicCommand.class.getName() ) ;
    final CtClass ctAugmented = ctPool.makeClass(
        commandNamePrefix +
            CaseFormat.LOWER_CAMEL.to( CaseFormat.UPPER_CAMEL, featureMethod.commandName ),
        ctBase
    ) ;
    if( commandClassEnhancer != null ) {
      final ImmutableMap< Class< ? >, Function< Method, String > > interfaceDefinition =
          commandClassEnhancer.moreImplementedInterfaces( featureMethod ) ;
      if( ! interfaceDefinition.isEmpty() ) {
        for( final Map.Entry< Class< ? >, Function< Method, String > > interfaceEntry :
            interfaceDefinition.entrySet()
        ) {
          final CtClass ctAdditionalInterface = ctPool.get( interfaceEntry.getKey().getName() ) ;
          ctAugmented.addInterface( ctAdditionalInterface ) ;
          for( final Method method : interfaceEntry.getKey().getDeclaredMethods() ) {
            final CtClass[] argumentTypes = CommandCookerTools.javaClassesToJavassist(
                ctPool, method.getParameterTypes() ) ;
            final CtMethod ctAdditionalMethod = CtNewMethod.abstractMethod(
                ctPool.get( method.getReturnType().getName() ),
                method.getName(),
                argumentTypes,
                null,
                ctAugmented
            ) ;
            ctAdditionalMethod.setBody( interfaceEntry.getValue().apply( method ) ) ;
            ctAugmented.addMethod( ctAdditionalMethod ) ;
          }
        }

      }
    }
    return ( Class< Command > ) ( Object ) ctAugmented.getClass() ;
  }

  protected Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER > create(
    final FeatureRegistry.FeatureMethod featureMethod,
    final Object[] arguments
  ) {
    throw new UnsupportedOperationException( "TODO" ) ;
  }

  @Override
  public String toString() {
    return ToStringTools.nameAndCompactHash( this ) + '{' +
        CommandCookerTools.classNames( featureRegistry.featureClasses ) + '}' ;
  }

  interface CommandClassEnhancer {
    /**
     *
     * @param featureMethod a non-{@code null} object representing the method call
     *     producing the {@link Command} object.
     * @return {@code null} if no additional behavior. Otherwise, returns a {@code Map}
     *     containing interfaces to implement as keys for the give {@link Command},
     *     and a {@code Function} able to produce method bodies.
     *     See <a href='http://www.csg.ci.i.u-tokyo.ac.jp/~chiba/javassist/tutorial/tutorial2.html#before' >Javassist tutorial</a>
     *     for special variables to use in method bodies.
     */
    ImmutableMap< Class< ? >, Function< Method, String > > moreImplementedInterfaces(
        FeatureRegistry.FeatureMethod featureMethod
    ) ;

  }


  interface PreambleEncoder< ENDPOINT_SPECIFIC > {
    void set( ENDPOINT_SPECIFIC endpointSpecific, String commandName ) ;
    void encode( PositionalFieldWriter positionalFieldWriter ) throws IOException ;
  }

  /**
   * So we can support an interleaved sequence like: timestamp, command name, session identifier.
   * Session identifier may be a part of the {@link DataInput} (like when reading some file),
   * or received from elsewhere (WebSocket session).
   */
  interface PreambleDecoder< ENDPOINT_SPECIFIC > {
    /**
     * Updates private state so next calls to {@link #endpointSpecific()} and {@link #commandName()}
     * will return decoded values, until next call to {@link #decode(PositionalFieldReader)}.
     * @param positionalFieldReader
     */
    void decode( PositionalFieldReader positionalFieldReader ) throws IOException ;

    String commandName() ;

    ENDPOINT_SPECIFIC endpointSpecific() ;
  }

  interface PreambleCodec< ENDPOINT_SPECIFIC >
      extends PreambleEncoder< ENDPOINT_SPECIFIC >, PreambleDecoder< ENDPOINT_SPECIFIC >
  { }

  public static String argumentName( final Type type, final int index ) {
    checkArgument( index >= 0 ) ;
    return CaseFormat.UPPER_CAMEL.to( CaseFormat.LOWER_CAMEL, type.getTypeName() ) ;
  }

  @SuppressWarnings( "ClassExplicitlyAnnotation" )
  private static class CommandDescription
      extends AnnotationLiteral< Command.Description >
      implements Command.Description
  {
    private final String name ;
    private final boolean persist ;
    private final boolean originAware ;
    private final boolean tracked ;

    public CommandDescription( final FeatureRegistry.FeatureMethod featureMethod ) {
      this.name = featureMethod.commandName ;
      this.originAware = featureMethod.commandAnnotation.originAware() ;
      this.persist = featureMethod.commandAnnotation.persist() ;
      this.tracked = featureMethod.commandAnnotation.tracked() ;
    }

    @Override
    public String name() {
      return name ;
    }

    @Override
    public boolean tracked() {
      return tracked ;
    }
  }

}