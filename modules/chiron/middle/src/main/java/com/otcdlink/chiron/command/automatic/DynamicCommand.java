package com.otcdlink.chiron.command.automatic;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.otcdlink.chiron.buffer.PositionalFieldWriter;
import com.otcdlink.chiron.command.Command;
import com.otcdlink.chiron.command.Stamp;
import com.otcdlink.chiron.command.codec.Codec;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

class DynamicCommand< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER >
    extends Command< ENDPOINT_SPECIFIC, CALLABLE_RECEIVER >
{
  private final FeatureRegistry.FeatureMethod featureMethod ;
  private final ImmutableMap< String, Object > specificArguments ;
  private final Description description ;
  private final ImmutableMap< Type, Codec > methodParametersCodecs ;


  public DynamicCommand(
      final Stamp identifier,
      final ENDPOINT_SPECIFIC endpointSpecific,
      final Description description,
      final ImmutableMap<Type, Codec> methodParametersCodecs,
      final FeatureRegistry.FeatureMethod featureMethod,
      final ImmutableMap<String, Object> specificArguments
  ) {
    super( endpointSpecific ) ;
    this.description = checkNotNull( description ) ;
    this.methodParametersCodecs = checkNotNull( methodParametersCodecs ) ;
    this.featureMethod = checkNotNull( featureMethod ) ;
    this.specificArguments = checkNotNull( specificArguments ) ;
  }

  @Override
  public Description description() {
    return description ;
  }

  @Override
  public void callReceiver( final CALLABLE_RECEIVER callableReceiver ) {
    final Object[] argumentArray = new Object[ specificArguments.size() + 1 ] ;
    argumentArray[ 0 ] = endpointSpecific ;
    final Iterator< Object > argumentIterator = specificArguments.values().iterator() ;
    for( int i = 1 ; i < argumentArray.length ; i ++ ) {
      argumentArray[ i ] = argumentIterator.next() ;
    }
    try {
      featureMethod.method.invoke( callableReceiver, argumentArray ) ;
    } catch( final IllegalAccessException | InvocationTargetException e ) {
      Throwables.propagate( e ) ;
    }
  }

  @Override
  public void encodeBody( final PositionalFieldWriter positionalFieldWriter ) throws IOException {
    for( final Map.Entry< String, Object > specificArgument : specificArguments.entrySet() ) {
      final Codec codec = methodParametersCodecs.get( specificArgument.getValue().getClass() ) ;
//      codec.encodeTo( specificArgument.getValue(), positionalFieldWriter ) ;
    }
  }

  @Override
  protected String toStringBody() {
    return
        "featureMethod=" + featureMethod.commandName + ';' +
        "specificArguments=" + specificArguments.toString() ;
  }

  @Override
  public boolean equals( final Object other ) {
    if( this == other ) {
      return true ;
    }
    if( other == null || getClass() != other.getClass() ) {
      return false ;
    }

    final DynamicCommand< ?, ? > that = ( DynamicCommand< ?, ? > ) other ;

    if( ! endpointSpecific.equals( that.endpointSpecific ) ) {
      return false ;
    }
    if( ! featureMethod.equals( that.featureMethod ) ) {
      return false ;
    }
    return specificArguments.equals( that.specificArguments ) ;
  }

}
