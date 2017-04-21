package io.github.otcdlink.chiron.middle.shaft;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface MethodCaller< TARGET > {
  void callMethods( TARGET target ) ;
  Class< TARGET > targetInterface() ;

  abstract class Default< TARGET > implements MethodCaller< TARGET > {
    private final Class< TARGET > targetInterface ;

    protected Default() {
      final Type type = ( ( ParameterizedType )
          getClass().getGenericSuperclass() ).getActualTypeArguments()[ 0 ] ;
      if( type instanceof Class ) {
        this.targetInterface = ( Class< TARGET > ) type ;
      } else if( type instanceof ParameterizedType ) {
        this.targetInterface = ( Class< TARGET > ) ( ( ParameterizedType ) type ).getRawType() ;
      } else {
        throw new Error( "Unsupported: " + type ) ;
      }

    }

    @Override
    public final Class< TARGET > targetInterface() {
      return targetInterface ;
    }

  }

}
