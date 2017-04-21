package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.AbstractInvocationHandler;
import io.github.otcdlink.chiron.toolbox.netty.NettyTools;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class ObjectTools {

  private ObjectTools() { }

  public static < T > Holder< T > newHolder() {
    return new Holder<>() ;
  }

  public static < T > ResettableHolder< T > newResettableHolder() {
    return new ResettableHolder<>() ;
  }

  public interface ReadableHolder< OBJECT > {
    OBJECT get() ;
  }

  protected static abstract class AbstractHolder< OBJECT > implements ReadableHolder< OBJECT > {
    private final AtomicReference< OBJECT > value = new AtomicReference<>() ;

    public void set( final OBJECT value ) {
      this.value.set( checkNotNull( value ) ) ;
    }

    public final OBJECT setMaybe( final OBJECT value ) {
      return this.value.getAndSet( checkNotNull( value ) ) ;
    }

    public final boolean isSet() {
      return value.get() != null ;
    }

    protected final void setSafe( final OBJECT value ) {
      checkState( this.value.getAndSet( value ) == null, "Value already set to " + value ) ;
    }

    @Override
    public OBJECT get() {
      return this.value.get() ;
    }

    protected final OBJECT getSafe() {
      final OBJECT current = this.value.get() ;
      checkState( current != null, "No value set" ) ;
      return current ;
    }

    public String toString() {
      return getClass().getSimpleName() + '@' + System.identityHashCode( this ) +
          '{' + String.valueOf( this.value.get() ) + '}' ;
    }
  }

  /**
   * An object container that can only be set once, with a non-{@code null} value, and checks
   * that value was previously initialized when getting it.
   */
  public static final class Holder< OBJECT > extends AbstractHolder< OBJECT > {

    private Holder() { }

    @Override
    public void set( final OBJECT value ) {
      setSafe( value ) ;
    }

    @Override
    public OBJECT get() {
      return getSafe() ;
    }
  }

  public static final class ResettableHolder< OBJECT > extends AbstractHolder< OBJECT > {

    private ResettableHolder() { }

  }


  public static < OBJECT > OBJECT nullObject(
      final Class interface1,
      final Class... otherInterfaces
  ) {
    final ImmutableList.Builder< Class > classListBuilder = ImmutableList.builder() ;
    classListBuilder.add( interface1 ) ;
    for( final Class other : otherInterfaces ) {
      classListBuilder.add( other ) ;
    }
    final ImmutableList< Class > implementedInterfaces = classListBuilder.build() ;
    final String interfaceNames = Joiner.on( ',' )
        .join( Iterables.transform( implementedInterfaces, Class::getName ) ) ;
    final Class[] implementedInterfacesAsArray = implementedInterfaces.toArray(
        new Class[ implementedInterfaces.size() ] ) ;

    final Object proxyInstance = Proxy.newProxyInstance(
        NettyTools.class.getClassLoader(),
        implementedInterfacesAsArray,
        new AbstractInvocationHandler() {
          @Override
          protected Object handleInvocation(
              final Object proxy,
              final Method method,
              final Object[] arguments
          ) {
            throw new UnsupportedOperationException( "Do not call on " + this + ": " + method ) ;
          }

          @Override
          public String toString() {
            return ObjectTools.class.getSimpleName() + "#nullObject{" + interfaceNames + "}";
          }
        }
    ) ;
    return ( OBJECT ) proxyInstance ;
  }
}
