package com.otcdlink.chiron.toolbox.collection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.SampleElements;
import com.google.common.collect.testing.TestMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.primitives.Longs;
import com.otcdlink.chiron.toolbox.ComparatorTools;
import com.otcdlink.chiron.toolbox.collection.ImmutableLongKeyHolderMapFixture.Entity;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static com.otcdlink.chiron.toolbox.collection.ImmutableLongKeyHolderMapFixture.asEntry;

public class ImmutableLongKeyHolderMapGuavaTest {

  public static Test suite() {
    final TestSuite testSuite = MapTestSuiteBuilder
        .using( newTestMapGenerator() )
        .named( "Tests for " + ImmutableLongKeyHolderMap.class.getSimpleName() )
        .withFeatures(
            CollectionSize.ANY,
            CollectionFeature.REJECTS_DUPLICATES_AT_CREATION,
            CollectionFeature.KNOWN_ORDER
        )
        .createTestSuite()
    ;
    removeTestsByName( ImmutableList.of( testSuite ), SKIP_LIST ) ;

    return testSuite ;
  }


// =======
// Fixture
// =======


  private static TestMapGenerator< Entity.Key, Entity> newTestMapGenerator() {
    return new TestMapGenerator< Entity.Key, Entity>() {
      @Override
      public SampleElements< Map.Entry< Entity.Key, Entity> > samples() {
        return new SampleElements<>(
            asEntry( new Entity( 0 ) ),
            asEntry( new Entity( 1 ) ),
            asEntry( new Entity( 2 ) ),
            asEntry( new Entity( 3 ) ),
            asEntry( new Entity( 4 ) )
        ) ;
      }

      @Override
      public Map< Entity.Key, Entity > create( Object... elements ) {
        final ImmutableList< Map.Entry< Entity.Key, Entity > > list = ( ImmutableList )
            ImmutableList.copyOf( elements ) ;
        return new ImmutableLongKeyHolderMap<>( list, true ) ;
      }

      @Override
      public Map.Entry< Entity.Key, Entity>[] createArray( int length ) {
        return new Map.Entry[ length ] ;
      }

      @Override
      public Iterable< Map.Entry< Entity.Key, Entity > > order(
          final List< Map.Entry< Entity.Key, Entity > > insertionOrder
      ) {
        Collections.sort(
            insertionOrder,
            new ComparatorTools.WithNull< Map.Entry< Entity.Key, Entity> >() {
              @Override
              protected int compareNoNulls(
                  final Map.Entry< Entity.Key, Entity> first,
                  final Map.Entry< Entity.Key, Entity> second
              ) {
                return Longs.compare( first.getKey().index(), second.getKey().index() ) ;
              }
            }
        ) ;
        return insertionOrder ;
      }

      @Override
      public Entity.Key[] createKeyArray( int length ) {
        return new Entity.Key[ length ] ;
      }

      @Override
      public Entity[] createValueArray( int length ) {
        return new Entity[ length ] ;
      }

    } ;
  }

  private static final Field F_TESTS_FIELDS ;
  private static final Field F_NAME ;

  static {
    try {
      F_TESTS_FIELDS = TestSuite.class.getDeclaredField( "fTests" ) ;
      F_NAME = TestCase.class.getDeclaredField( "fName" ) ;
    } catch( final NoSuchFieldException e ) {
      throw new RuntimeException( e ) ;
    }
    F_TESTS_FIELDS.setAccessible( true ) ;
    F_NAME.setAccessible( true ) ;
  }

  private static Vector< Test > extractTests( final TestSuite testSuite ) {
    try {
      return ( Vector< Test > ) F_TESTS_FIELDS.get( testSuite ) ;
    } catch( IllegalAccessException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static String extractName( final TestCase testCase ) {
    try {
      return ( String ) F_NAME.get( testCase ) ;
    } catch( IllegalAccessException e ) {
      throw new RuntimeException( e ) ;
    }
  }

  private static void removeTestsByName(
      final List< Test > tests,
      final ImmutableList< String > testNames
  ) {
    final Iterator< Test > iterator = tests.iterator() ;
    while( iterator.hasNext() ) {
      final Test test = iterator.next() ;
      if( test instanceof TestSuite ) {
        removeTestsByName( extractTests( ( TestSuite ) test ), testNames ) ;
      }
      if( test instanceof TestCase && testNames.contains( extractName( ( TestCase ) test ) ) ) {
        iterator.remove() ;
      }
    }
  }

  private static final ImmutableList< String > SKIP_LIST = ImmutableList.of(
      // Broken by inconsistency detection between key and value.
      "testCreateWithDuplicates_nonNullDuplicatesNotRejected",

      // Should not happen.
      "testContainsEntryWithIncomparableValue"

  ) ;
}