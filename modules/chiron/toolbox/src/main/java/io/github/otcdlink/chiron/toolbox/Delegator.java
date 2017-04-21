package io.github.otcdlink.chiron.toolbox;

import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static com.google.common.base.Preconditions.checkState;

/**
 * Creates a proxy object referencing a delegate to be set afterwards.
 * This is useful for resolving dependency cycles among components.
 *
 * TODO: use Javassist to create bytecode-based proxies instead of {@code Proxy}-based ones.
 */
public class Delegator< T > {

  private T delegate = null ;
  private final T proxy ;

  protected Delegator( final Class< T > proxiedClass ) {
    Preconditions.checkArgument( proxiedClass.isInterface() ) ;

    final Object proxyInstance = Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[]{ proxiedClass },
        ( proxy1, method, arguments ) -> {
          checkState( delegate != null, "Delegate not set" ) ;
          return doInvoke( delegate, method, arguments ) ;
        }
    ) ;
    proxy = ( T ) proxyInstance ;
  }

  protected Object doInvoke(
      final Object delegate,
      final Method method,
      final Object[] arguments
  ) throws IllegalAccessException, InvocationTargetException {
    return method.invoke( delegate, arguments ) ;
  }

  public static < T > Delegator< T > create( final Class< T > proxiedClass ) {
    return new Delegator<>( proxiedClass ) ;
  }

  public static < T > Delegator< T > create( final TypeToken< T > proxiedClass ) {
    return new Delegator<>( ( Class< T > ) proxiedClass.getRawType() ) ;
  }

  public final void setDelegate( final T delegate ) {
    setDelegate( delegate, false ) ;
  }

  public final void setDelegate( final T delegate, final boolean mayReassign ) {
    if( ! mayReassign ) {
      checkState( this.delegate == null, "Delegate already set" ) ;
    }
    this.delegate = delegate ;
  }

  public final T getProxy() {
    return proxy ;
  }
}
