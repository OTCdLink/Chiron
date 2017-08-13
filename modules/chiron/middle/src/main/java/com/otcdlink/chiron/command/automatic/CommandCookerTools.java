package com.otcdlink.chiron.command.automatic;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

final class CommandCookerTools {
  private CommandCookerTools() { }

  public static Iterable< String > classNames( final ImmutableList< Class< ? > > list ) {
    return Iterables.transform( list, new Function< Class< ? >, String>() {
      @Nullable
      @Override
      public String apply( final Class< ? > input ) {
        return input.getName() ;
      }
    } ) ;
  }

  public static boolean callingObject$equals( final Method method, final Object... arguments ) {
    return "equals".equals( method.getName() ) && arguments.length == 1 ;
  }

  public static boolean callingObject$hashCode( final Method method, final Object[] arguments ) {
    return "hashCode".equals( method.getName() ) && ( arguments == null || arguments.length == 0 ) ;
  }

  public static boolean callingObject$toString( final Method method, final Object[] arguments ) {
    return "toString".equals( method.getName() ) && ( arguments == null || arguments.length == 0 ) ;
  }

  public static CtClass[] javaClassesToJavassist(
      final ClassPool ctPool,
      final Class< ? >[] parameterTypes
  ) throws NotFoundException {
    final CtClass[] ctClasses = new CtClass[ parameterTypes.length ] ;
    for( int i = 0 ; i < parameterTypes.length ; i ++ ) {
      final Class clasS = parameterTypes[ i ] ;
      final CtClass ctClass ;
      if( clasS.equals( Byte.TYPE ) ) {
        ctClass = CtClass.byteType;
      } else if( clasS.equals( Short.TYPE ) ) {
        ctClass = CtClass.shortType ;
      } else if( clasS.equals( Boolean.TYPE ) ) {
        ctClass = CtClass.booleanType ;
      } else if( clasS.equals( Character.TYPE ) ) {
        ctClass = CtClass.charType ;
      } else if( clasS.equals( Double.TYPE ) ) {
        ctClass = CtClass.doubleType ;
      } else if( clasS.equals( Float.TYPE ) ) {
        ctClass = CtClass.floatType ;
      } else if( clasS.equals( Integer.TYPE ) ) {
        ctClass = CtClass.intType ;
      } else if( clasS.equals( Long.TYPE ) ) {
        ctClass = CtClass.longType ;
      } else {
        ctClass = ctPool.getCtClass( clasS.getName() ) ;
      }
      ctClasses[ i ] = ctClass ;
    }
    return ctClasses ;
  }
}
