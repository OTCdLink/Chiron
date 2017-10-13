package com.otcdlink.chiron.toolbox.collection;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

final class AutoconstantTools {
  private AutoconstantTools() { }

  /**
   * Copied from {@link io.netty.util.internal.TypeParameterMatcher}, Netty 4.10.
   */
  public static Class< ? > find0(
      final Object object, 
      Class< ? > parametrizedSuperclass, 
      String typeParamName 
  ) {

    final Class< ? > thisClass = object.getClass() ;
    Class< ? > currentClass = thisClass ;
    for( ; ; ) {
      if( currentClass.getSuperclass() == parametrizedSuperclass ) {
        int typeParamIndex = -1 ;
        TypeVariable< ? >[] typeParams = currentClass.getSuperclass().getTypeParameters() ;
        for( int i = 0 ; i < typeParams.length ; i++ ) {
          if( typeParamName.equals( typeParams[ i ].getName() ) ) {
            typeParamIndex = i ;
            break;
          }
        }

        if( typeParamIndex < 0 ) {
          throw new IllegalStateException(
              "unknown type parameter '" + typeParamName + "': " + parametrizedSuperclass ) ;
        }

        final Type genericSuperType = currentClass.getGenericSuperclass() ;
        if( !( genericSuperType instanceof ParameterizedType ) ) {
          return Object.class ;
        }

        final Type[] actualTypeParams =
            ( ( ParameterizedType ) genericSuperType ).getActualTypeArguments() ;

        Type actualTypeParam = actualTypeParams[ typeParamIndex ] ;
        if( actualTypeParam instanceof ParameterizedType ) {
          actualTypeParam = ( ( ParameterizedType ) actualTypeParam ).getRawType() ;
        }
        if( actualTypeParam instanceof Class ) {
          return ( Class< ? > ) actualTypeParam ;
        }
        if( actualTypeParam instanceof GenericArrayType ) {
          Type componentType = ( ( GenericArrayType ) actualTypeParam ).getGenericComponentType() ;
          if( componentType instanceof ParameterizedType ) {
            componentType = ( ( ParameterizedType ) componentType ).getRawType() ;
          }
          if( componentType instanceof Class ) {
            return Array.newInstance( ( Class< ? > ) componentType, 0 ).getClass() ;
          }
        }
        if( actualTypeParam instanceof TypeVariable ) {
          // Resolved type parameter points to another type parameter.
          final TypeVariable< ? > v = ( TypeVariable< ? > ) actualTypeParam ;
          currentClass = thisClass ;
          if( !( v.getGenericDeclaration() instanceof Class ) ) {
            return Object.class ;
          }

          parametrizedSuperclass = ( Class< ? > ) v.getGenericDeclaration() ;
          typeParamName = v.getName() ;
          if( parametrizedSuperclass.isAssignableFrom( thisClass ) ) {
            continue ;
          } else {
            return Object.class ;
          }
        }

        return fail( thisClass, typeParamName ) ;
      }
      currentClass = currentClass.getSuperclass() ;
      if( currentClass == null ) {
        return fail( thisClass, typeParamName ) ;
      }
    }
  }

  private static Class< ? > fail( final Class< ? > type, final String typeParamName ) {
    throw new Autoconstant.DeclarationException(
        "cannot determine the type of the type parameter '" + typeParamName + "': " + type ) ;
  }

}
