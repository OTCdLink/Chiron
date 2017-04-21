package io.github.otcdlink.chiron.testing;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Experimental approach to extract test name without {@link org.junit.rules.MethodRule}, which
 * doesn't play nicely with final fields.
 *
 * http://stackoverflow.com/a/758180
 */
public class NameAwareRunner extends BlockJUnit4ClassRunner {

  public NameAwareRunner( final Class< ? > aClass ) throws InitializationError {
    super( aClass ) ;
  }

  private static final ThreadLocal< Method > testMethod = new ThreadLocal<>() ;

  @Override
  protected Statement methodBlock( final FrameworkMethod frameworkMethod ) {
    testMethod.set( frameworkMethod.getMethod() ) ;
    return super.methodBlock( frameworkMethod ) ;
  }

  public static String getTestShortName() {
    return testMethod.get().getName() ;
  }

  public static String getTestLongName() {
    return testMethod.get().getDeclaringClass().getName() + "#" + getTestShortName() ;
  }

  public static Method getTestMethod() {
    return testMethod.get() ;
  }

  public static File testDirectory() throws IOException {
    return new DirectoryFixture( getTestLongName() ).getDirectory() ;
  }


}
