package com.otcdlink.chiron.evaluator;

import com.google.common.base.Converter;
import com.google.common.collect.ImmutableBiMap;
import com.otcdlink.chiron.toolbox.ToStringTools;

import static com.google.common.base.Preconditions.checkNotNull;

public interface OperatorCare<
    OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
    OPERATOR_CONTEXT,
    PARAMETER,
    VALUE
> {
  ImmutableBiMap< String, OPERATOR > symbolMap() ;
  Converter< String, PARAMETER > parameterConverter() ;

  static<
      OPERATOR extends Enum< OPERATOR > & Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
      OPERATOR_CONTEXT,
      PARAMETER,
      VALUE
  > OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > newFromEnum(
      final Class< OPERATOR > enumClass,
      final Converter< String, PARAMETER > parameterConverter
  ) {
    final OPERATOR[] enumConstants = enumClass.getEnumConstants() ;
    final ImmutableBiMap.Builder< String, OPERATOR > builder = ImmutableBiMap.builder() ;
    for( final OPERATOR operator : enumConstants ) {
      builder.put( operator.symbol(), operator ) ;
    }
    final ImmutableBiMap< String, OPERATOR > symbolMap = builder.build() ;

    return new Default<>( symbolMap, parameterConverter ) ;
  }

  class Default<
      OPERATOR extends Operator< OPERATOR_CONTEXT, PARAMETER, VALUE >,
      OPERATOR_CONTEXT,
      PARAMETER,
      VALUE
  > implements OperatorCare< OPERATOR, OPERATOR_CONTEXT, PARAMETER, VALUE > {

    private final ImmutableBiMap< String, OPERATOR > symbolMap ;
    private final Converter< String, PARAMETER > parameterConverter ;

    public Default(
        final ImmutableBiMap< String, OPERATOR > symbolMap,
        final Converter< String, PARAMETER > parameterConverter
    ) {
      this.symbolMap = checkNotNull( symbolMap ) ;
      this.parameterConverter = checkNotNull( parameterConverter ) ;
    }

    @Override
    public ImmutableBiMap< String, OPERATOR > symbolMap() {
      return symbolMap ;
    }

    @Override
    public Converter< String, PARAMETER > parameterConverter() {
      return parameterConverter ;
    }

    @Override
    public String toString() {
      return ToStringTools.getNiceClassName( this ) + "{" +
          "symbolMap=" + symbolMap +
          ", parameterConverter=" + parameterConverter +
          '}'
      ;
    }
  }
}
