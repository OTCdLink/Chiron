package com.otcdlink.chiron.testing;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.otcdlink.chiron.testing.junit5.DirectorySupplier;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * Works for JUnit 4 as a {@link TestRule} and JUnit 5 as an Extension.
 */
public class MethodSupport
    implements
        TestRule,
        Supplier< File >,
        BeforeTestExecutionCallback,
        AfterTestExecutionCallback
{

  private static final Logger LOGGER = LoggerFactory.getLogger( MethodSupport.class ) ;
  private static final Pattern SAFE_METHOD_NAME_PATTERN = Pattern.compile( "(^[a-zA-Z0-9_$]+).*" ) ;


  private String testName = null ;
  private Method testMethod = null ;

  private DirectoryFixture directoryFixture = null ;

  /**
   * Synchronize every access to object's fields on this object.
   */
  private final Object stateLock = new Object() ;

  private final Object executionLock ;

  public MethodSupport() {
    this( null ) ;
  }

  public MethodSupport( final Object executionLock ) {
    this.executionLock = executionLock ;
  }

  public static String shortenTestName( final String testName ) {
    return shortenTestName( testName, 6 ) ;
  }

  public static String shortenTestName( final String testName, final int maximumLength ) {
    final String shortName = testName.substring( 0, Math.min( maximumLength, testName.length() ) ) +
        ( testName.length() > maximumLength ? "_" : "" ) ;
    return shortName ;
  }

  @Override
  public Statement apply(
      final Statement base,
      final Description description
  ) {
    synchronized( stateLock ) {
      testName = description.getMethodName() ;
      // Parameterized tests have a description that contains more than method's name.
      final String safeMethodName = SAFE_METHOD_NAME_PATTERN.matcher( description.getMethodName() )
          .replaceAll( "$1" ) ;
      try {
        testMethod = description.getTestClass().getMethod( safeMethodName ) ;
      } catch( final NoSuchMethodException e ) {
        throw Throwables.propagate( e ) ;
      }
      final String reasonToNotApply = mayEvaluateInContext() ;
      if( reasonToNotApply == null ) {
        return new WrappedStatement( base, executionLock ) ;
      } else {
        return new UnappliableStatement( reasonToNotApply ) ;
      }
    }
  }

  public final String getTestName() {
    synchronized( stateLock ) {
      Preconditions.checkState( testName != null ) ;
      return testName ;
    }
  }

  public final Method getTestMethod() {
    synchronized( stateLock ) {
      Preconditions.checkState( testMethod != null ) ;
      return testMethod ;
    }
  }

  public final File getDirectory() {
    synchronized( stateLock ) {
      if( directoryFixture == null ) {
        directoryFixture = new DirectoryFixture( getTestName() ) ;
      }
      try {
        return directoryFixture.getDirectory() ;
      } catch( final IOException e ) {
        throw new RuntimeException( e ) ;
      }
    }
  }

  @Override
  public File get() {
    return getDirectory() ;
  }

  protected void beforeStatementEvaluation() throws Exception { }


  protected void afterStatementEvaluation() throws Exception { }

  /**
   * Override to disable the test on a contextual basis (where {@code @Ignore} wouldn't fit).
   * Return a non-null {@code String} to indicate the {@link Statement} shouldn't evaluate,
   * indicating the reason why.
   *
   * @return null by default.
   */
  protected String mayEvaluateInContext() {
    return null ;
  }


  private class WrappedStatement extends Statement {
    protected final Statement base ;
    private final Object lock ;

    public WrappedStatement( final Statement base, final Object lock ) {
      this.base = base ;
      this.lock = lock ;
    }

    @Override
    public void evaluate() throws Throwable {
      final String shortName = shortenTestName( getTestName() ) ;
      Thread.currentThread().setName( "test-" + shortName ) ;
      if( lock == null ) {
        doEvaluate() ;
      } else {
        synchronized( lock ) {
          doEvaluate() ;
        }
      }
    }

    private void doEvaluate() throws Throwable {
      LOGGER.info( "*** Evaluating " + getTestName() + "... ***" );
      try {
        beforeStatementEvaluation() ;
        base.evaluate() ;
      } catch( final Throwable throwable ) {
        LOGGER.error( "Test failed.", throwable ) ;
        Throwables.propagateIfPossible( throwable ) ;
        // Past the line above, this is a checked exception.
        throw new RuntimeException( throwable ) ;
      } finally {
        try {
          afterStatementEvaluation() ;
        } finally {
          LOGGER.info( "*** Done with " + getTestName() + ". ***" ) ;
        }
      }
    }
  }

  private class UnappliableStatement extends Statement {

    private final String reason ;

    public UnappliableStatement( final String reason ) {
      this.reason = reason ;
    }

    @Override
    public void evaluate() throws Throwable {
      LOGGER.info( "*** Skipping " + getTestName() + " because " + reason  + " ***" ) ;
    }
  }

// =======
// JUnit 5
// =======


  @Override
  public void beforeTestExecution( final ExtensionContext context ) throws Exception {
    synchronized( stateLock ) {
      testName = context.getUniqueId() ;
      testMethod = context.getTestMethod().orElse( null ) ;
      directoryFixture = DirectorySupplier.loadInScope( context ) ;
    }
    beforeStatementEvaluation() ;
  }

  @Override
  public void afterTestExecution( final ExtensionContext context ) throws Exception {
    afterStatementEvaluation() ;
  }
}
