package com.otcdlink.chiron.command.automatic;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavassistDemo {


  @Test
  @Ignore( "Not needed in a normal build")
  public void deriveAndAddInterface() throws Exception {
    final ClassPool ctPool = new ClassPool( true ) ;
    final CtClass ctBase = ctPool.get( Base.class.getName() ) ;
    final CtClass ctAugmented = ctPool.get( Augmented.class.getName() ) ;

    final CtClass ctDerived = ctPool.makeClass( "Derived", ctBase ) ;

    {
      ctDerived.addInterface( ctAugmented ) ;
      final CtField ctCounterField = CtField.make( "private int counter = 0 ;", ctDerived ) ;
      ctDerived.addField( ctCounterField ) ;

      final CtMethod ctIncrementMethod = CtNewMethod.abstractMethod(
          CtClass.intType, "increment", new CtClass[]{ CtClass.intType }, null, ctDerived ) ;

      ctIncrementMethod.setBody(
          "{ counter = counter + $1 ; return counter ; } " ) ;

      ctDerived.addMethod( ctIncrementMethod ) ;

    }

//    ctDerived.debugWriteFile() ;  // Creates an annoying 'Derived.class' file somewhere.

    final Class derivedClass = ctDerived.toClass() ;
    final Object enhanced = derivedClass.newInstance() ;
    assertThat( enhanced ).isInstanceOf( Base.class ) ;
    assertThat( enhanced ).isInstanceOf( Augmented.class ) ;

    assertThat( ( ( Base ) enhanced ).hello() ).isEqualTo( "Hello" ) ;
    assertThat( ( ( Augmented ) enhanced ).increment( 3 ) ).isEqualTo( 3 ) ;
    assertThat( ( ( Augmented ) enhanced ).increment( 3 ) ).isEqualTo( 6 ) ;

  }


  public static class Base {
    public String hello() {
      return "Hello" ;
    }
  }

  public interface Augmented {
    int increment( int delta ) ;
  }
}
