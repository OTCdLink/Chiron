package io.github.otcdlink.chiron.middle.session;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.github.otcdlink.chiron.buffer.PositionalFieldReader;
import io.github.otcdlink.chiron.buffer.PositionalFieldWriter;
import io.github.otcdlink.chiron.codec.DecodeException;
import io.github.otcdlink.chiron.command.Command;
import io.github.otcdlink.chiron.toolbox.EnumTools;
import io.github.otcdlink.chiron.toolbox.ToStringTools;
import io.netty.channel.ChannelHandler;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Defines strongly typed structures for Signon.
 *
 * <h1>Serialization with {@link PositionalFieldReader} and {@link PositionalFieldWriter}</h1>
 * <p>
 * This is directly supported to avoid double URLencoding.
 *
 * <h1>Serialization as text</h1>
 * <p>
 * Serialized form looks like this (there is no space, tab or line break):
 * <pre>
 PRIMARY_SIGNON:LOGIN=userLogin;PASSWORD=userPassword;
 or:
 SIGNON_FAILED:SIGNON_FAILURE_NOTICE=INVALID_CREDENTIAL+someMessage;
 </pre>
 *
 * <h1>Design decisions</h1>
 * <p>
 * A {@link Phase} looks like a {@link Command}, but a {@link Command#endpointSpecific}
 * often carries a {@link SessionIdentifier} that a {@link Phase} couldn't know about
 * (precisely because we use {@link Phase} objects to create a session).
 *
 * TODO: specialise the {@link Phase} into {@code Phase.Upward} and {@code Phase.Downward},
 * using tagging interfaces.
 */
public final class SessionLifecycle {

  private SessionLifecycle() { }

  /**
   * Names must match {@link SessionLifecycle.Phase} subinterfaces.
   */
  public enum Kind {
    PRIMARY_SIGNON_NEEDED,
    PRIMARY_SIGNON,
    SECONDARY_SIGNON_NEEDED,
    SECONDARY_SIGNON,
    SIGNON_FAILED,
    SESSION_VALID,
    RESIGNON,
    SIGNOFF,
    KICKOUT,
    TIMEOUT,
    ;
  }

  public interface PrimarySignonNeeded extends Phase {
    static PrimarySignonNeeded create() {
      return SessionLifecycle.create(
          PrimarySignonNeeded.class
      ) ;
    }
  }

  public interface PrimarySignon extends Phase {
    String login() ;
    String password() ;

    static PrimarySignon create( final String login, final String password ) {
      return SessionLifecycle.create(
          PrimarySignon.class,
          Key.LOGIN, login,
          Key.PASSWORD, password
      ) ;
    }
  }

  public interface SecondarySignonNeeded extends Phase {
    SecondaryToken secondaryToken() ;

    static SecondarySignonNeeded create( final SecondaryToken secondaryToken ) {
      return SessionLifecycle.create(
          SecondarySignonNeeded.class,
          Key.SECONDARY_TOKEN, secondaryToken
      ) ;
    }
  }

  public interface SecondarySignon extends Phase {
    SecondaryToken secondaryToken() ;
    SecondaryCode secondaryCode() ;

    static SecondarySignon create(
        final SecondaryToken secondaryToken,
        final SecondaryCode secondaryCode
    ) {
      return SessionLifecycle.create(
          SecondarySignon.class,
          Key.SECONDARY_TOKEN, secondaryToken,
          Key.SECONDARY_CODE, secondaryCode
      ) ;
    }
  }

  public interface SignonFailed extends Phase {
    SignonFailureNotice signonFailureNotice() ;

    static SignonFailed create( final SignonFailureNotice signonFailureNotice ) {
      final SignonFailed signonFailed = SessionLifecycle.create(
          SignonFailed.class,
          Key.SIGNON_FAILURE_NOTICE, signonFailureNotice
      ) ;
      return signonFailed ;
    }
  }

  public interface Resignon extends Phase {
    SessionIdentifier sessionIdentifier() ;

    static Resignon create( final SessionIdentifier sessionIdentifier ) {
      return SessionLifecycle.create(
          Resignon.class,
          Key.SESSION_IDENTIFIER, sessionIdentifier
      ) ;
    }
  }

  public interface SessionValid extends Phase {
    SessionIdentifier sessionIdentifier() ;

    static SessionValid create( final SessionIdentifier sessionIdentifier ) {
      return SessionLifecycle.create(
          SessionValid.class,
          Key.SESSION_IDENTIFIER, sessionIdentifier
      ) ;
    }
  }

  public interface Signoff extends Phase {
    static Signoff create() {
      return SessionLifecycle.create( Signoff.class ) ;
    }
  }

  public interface Kickout extends Phase {
    static Kickout create() {
      return SessionLifecycle.create( Kickout.class ) ;
    }
  }

  public interface Timeout extends Phase {
    static Timeout create() {
      return SessionLifecycle.create( Timeout.class ) ;
    }
  }


// ======
// Shared
// ======

  public interface Phase {
    ImmutableMap< Key, Object > keyValuePairs() ;

    String asString() ;

    Kind kind() ;

    /**
     * Returns the interface representing the main role of this {@link Phase}.
     * This is only used internally but it is harmless to expose it in a public interface.
     */
    Class< ? extends Phase > phaseClass() ;
  }


  public static < PHASE extends Phase > String serialize( final PHASE phase ) {
    return phase.kind().name() + ':' + phase.asString() ;
  }

  public static < PHASE extends Phase > PHASE deserialize( final String string )
      throws DecodeException
  {
    final Kind kind ;
    final String keyValuePairsAsStrings ;
    {
      final int firstIndexOfSemicolon = string.indexOf( ':' ) ;
      if( firstIndexOfSemicolon <= 0 ) {
        throw new DecodeException( "Missing kind at start in '" + string + "'" ) ;
      }
      final String kindAsString = string.substring( 0, firstIndexOfSemicolon ) ;
      kind = resolveKind( kindAsString ) ;
      keyValuePairsAsStrings = string.substring( firstIndexOfSemicolon + 1 ) ;
    }

    final Iterable< String > keyValuePairs = Splitter.on( ';' )
        .omitEmptyStrings().split( keyValuePairsAsStrings ) ;

    final SortedMap< Key, Object > map = new TreeMap<>() ;
    for( final String keyValuePair : keyValuePairs ) {
      final List< String > tokens = Splitter.on( '=' ).splitToList( keyValuePair ) ;
      if( tokens.size() != 2 ) {
        throw new DecodeException( "Malformed key-value pair '" + keyValuePair + "' in " +
            "'" + string + "'" ) ;
      }
      final String keyAsString = tokens.get( 0 ) ;
      final Key key = resolveKey( keyAsString ) ;
      final String valueAsString = tokens.get( 1 ) ;
      map.put(
          key,
          "!".equals( valueAsString ) ? null : key.decode( urldecodeQuiet( valueAsString ) )
      ) ;
    }
    return createPhaseObject( kind, map ) ;
  }

  public static < PHASE extends Phase > void serialize(
      final PositionalFieldWriter positionalFieldWriter,
      final PHASE phase
  ) {
    positionalFieldWriter.writeDelimitedString( phase.kind().name() ) ;
    for( final Map.Entry< Key, Object > entry : phase.keyValuePairs().entrySet() ) {
      positionalFieldWriter.writeDelimitedString( entry.getKey().name() ) ;
      final String valueString = entry.getValue() == null ?
          null : entry.getKey().encode( entry.getValue() ) ;
      positionalFieldWriter.writeNullableString( valueString ) ;
    }
  }

  public static < PHASE extends Phase > PHASE deserialize(
      final PositionalFieldReader positionalFieldReader
  ) throws DecodeException {
    final Kind kind = resolveKind( positionalFieldReader.readDelimitedString() ) ;

    final SortedMap< Key, Object > map = new TreeMap<>() ;
    while( positionalFieldReader.readableBytes() > 0 ) {
      final String keyAsString = positionalFieldReader.readDelimitedString() ;
      final String valueAsString = positionalFieldReader.readDelimitedString() ;
      final Key key = resolveKey( keyAsString ) ;
      final Object value = valueAsString == null ? null : key.decode( valueAsString ) ;
      map.put( key, value ) ;
    }
    return createPhaseObject( kind, map ) ;
  }

  private static Kind resolveKind( final String kindAsString )
      throws DecodeException
  {
    final Kind kind = EnumTools.fromNameLenient( Kind.values(), kindAsString ) ;
    if( kind == null ) {
      throw new DecodeException(
          "Unknown " + Kind.class + " '" + kindAsString + "' " +
              "(supported values are " + Arrays.asList( Kind.values() ) + ")"
      ) ;
    }
    return kind ;
  }

  private static Key resolveKey( final String keyAsString ) throws DecodeException {
    final Key key = EnumTools.fromNameLenient( Key.values(), keyAsString ) ;
    if( key == null ) {
      throw new DecodeException(
          "Unknown key '" + keyAsString + "'" ) ;
    }
    return key ;
  }

  private static < PHASE extends Phase > PHASE createPhaseObject(
      final Kind kind,
      final Map< Key, Object > map
  ) {
    final Class< ? extends Phase> phaseClass = KIND_CLASS_MAP.get( kind ) ;
    //noinspection unchecked
    return create( ( Class< PHASE > ) phaseClass, ImmutableMap.copyOf( map ) ) ;
  }



  private final static ImmutableBiMap< Kind, Class< ? extends Phase > > KIND_CLASS_MAP ;
  private final static ImmutableMap< Kind, ImmutableBiMap< Key, Method > > KEY_METHOD_MAP ;
  private final static Method PHASE_METHOD_KEYVALUEPAIRS ;
  private final static Method PHASE_METHOD_ASSTRING ;
  private final static Method PHASE_METHOD_KIND ;
  private final static Method PHASE_METHOD_PHASECLASS ;
  private final static Method PHASE_METHOD_EQUALS ;
  private final static Method PHASE_METHOD_HASHCODE ;
  private final static Method PHASE_METHOD_TOSTRING ;
  static {
    final ImmutableBiMap.Builder< Kind, Class< ? extends Phase > > kindClassBuilder =
        ImmutableBiMap.builder() ;
    final ImmutableMap.Builder< Kind, ImmutableBiMap< Key, Method > > keyMethodMapBuilder =
        ImmutableMap.builder() ;
    final Kind[] kindValues = Kind.values() ;
    final Class< ? >[] declaredClasses = SessionLifecycle.class.getDeclaredClasses() ;
    for( final Class< ? > declaredClass : declaredClasses ) {
      final SortedMap< Key, Method > methodMapBuilder = new TreeMap<>() ;
      if( declaredClass.isInterface() && implementsInterface( declaredClass, Phase.class ) ) {
        final Kind kind = EnumTools.javaNameToEnumLenient(
            kindValues, declaredClass.getSimpleName() ) ;
        if( kind == null ) {
          throw new DeclarationError( "Can't resolve '" + declaredClass.getSimpleName() + "' " +
              "as enum element of " + Arrays.asList( kindValues ) ) ;
        } else {
          //noinspection unchecked
          kindClassBuilder.put( kind, ( Class< ? extends Phase > ) declaredClass ) ;
          for( final Method method : declaredClass.getDeclaredMethods() ) {
            if( ! Modifier.isStatic( method.getModifiers() ) ) {
              if( method.getParameterCount() > 0 ) {
                throw new DeclarationError( "Method " + method + " should not take parameters" ) ;
              }
              final Key key = EnumTools.javaNameToEnumLenient( Key.values(), method.getName() ) ;
              if( key == null ) {
                throw new DeclarationError( "Method " + method + " doesn't match any of " +
                    Arrays.asList( Key.values( ) ) ) ;
              }
              methodMapBuilder.put( key, method ) ;
            }
          }
          keyMethodMapBuilder.put( kind, ImmutableBiMap.copyOf( methodMapBuilder ) ) ;
        }
      }
    }
    KEY_METHOD_MAP = keyMethodMapBuilder.build() ;
    KIND_CLASS_MAP = kindClassBuilder.build() ;
    try {
      PHASE_METHOD_KEYVALUEPAIRS = Phase.class.getDeclaredMethod( "keyValuePairs" ) ;
      PHASE_METHOD_ASSTRING = Phase.class.getDeclaredMethod( "asString" ) ;
      PHASE_METHOD_KIND = Phase.class.getDeclaredMethod( "kind" ) ;
      PHASE_METHOD_PHASECLASS = Phase.class.getDeclaredMethod( "phaseClass") ;
      PHASE_METHOD_EQUALS = Object.class.getDeclaredMethod( "equals", Object.class ) ;
      PHASE_METHOD_HASHCODE = Object.class.getDeclaredMethod( "hashCode" ) ;
      PHASE_METHOD_TOSTRING = Object.class.getDeclaredMethod( "toString") ;
    } catch( final NoSuchMethodException e ) {
      throw new DeclarationError( e ) ;
    }
  }

  private static boolean implementsInterface(
      final Class< ? > someClass,
      final Class< Phase > interfaceToImplement
  ) {
    return Arrays.asList( someClass.getInterfaces() ).contains( interfaceToImplement ) ;
  }

  public static final class DeclarationError extends Error {
    public DeclarationError( final String message ) {
      super( message ) ;
    }

    public DeclarationError( final Exception e ) {
      super( e ) ;
    }
  }

  private static < PHASE extends Phase > PHASE create( final Class< PHASE > phaseClass ) {
    return create( phaseClass, ImmutableMap.of() ) ;
  }

  private static < PHASE extends Phase > PHASE create(
      final Class< PHASE > phaseClass,
      final Key key1, final Object object1
  ) {
    return create( phaseClass, ImmutableMap.of( key1, object1 ) ) ;
  }

  private static < PHASE extends Phase > PHASE create(
      final Class< PHASE > phaseClass,
      final Key key1, final Object object1,
      final Key key2, final Object object2
  ) {
    return create( phaseClass, ImmutableMap.of( key1, object1, key2, object2 ) ) ;
  }

  private static < PHASE extends Phase > PHASE create(
      final Class< PHASE > phaseClass,
      final ImmutableMap< Key, Object > keyValuePairs
  ) {
    return create( phaseClass, EMPTY_FEATURE_MAP, keyValuePairs ) ;
  }

  /**
   * Dynamically adds new interfaces (and implementors through delegation) to an existing
   * {@link PHASE} created with one of the {@code create} methods of the {@link SessionLifecycle}
   * class (or one of the {@code create} method of one of its nested classes).
   * <p>
   * If given {@link PHASE} was already "featurized" the returned instance only contains the
   * new features.
   * <p>
   * This method is equivalent to a dynamic derivation of an existing class, but the reliance
   * on {@link Kind} would make standard derivation more complicated. Since a {@link Phase} is
   * the general contract between several {@link ChannelHandler}s, dynamic derivation doesn't
   * add much obscurity to a behavior that would require casting anyways.
   */
  public static < PHASE extends Phase > PHASE featurize(
      final PHASE phase,
      final FeatureMapper featureMapper
  ) {
    return create(
        ( Class< PHASE > ) phase.phaseClass(), featureMapper, phase.keyValuePairs() ) ;
  }

    /**
     * @param features non-null object for adding features (interface implementations delegating
     *     to the given object).
     */
  private static < PHASE extends Phase > PHASE create(
      final Class< PHASE > phaseClass,
      final ImmutableClassToInstanceMap< Object > features,
      final ImmutableMap< Key, Object > keyValuePairs
  ) {
    return create( phaseClass, new FeatureMapper( features ), keyValuePairs ) ;
  }

  private static < PHASE extends Phase > PHASE create(
      final Class< PHASE > phaseClass,
      final FeatureMapper featureMapper,
      final ImmutableMap< Key, Object > keyValuePairs
  ) {

    final Kind kind = KIND_CLASS_MAP.inverse().get( phaseClass ) ;
    final Object proxyInstance = Proxy.newProxyInstance(
        SessionLifecycle.class.getClassLoader(),
        featureMapper.addFeatureInterfaces( phaseClass ),
        ( proxy, method, args ) -> {
          if( method.equals( PHASE_METHOD_ASSTRING ) ) {
            return asString( keyValuePairs, true ) ;
          } else if( method.equals( PHASE_METHOD_KEYVALUEPAIRS ) ) {
            return keyValuePairs ;
          } else if( method.equals( PHASE_METHOD_PHASECLASS ) ) {
            return phaseClass ;
          } else if( method.equals( PHASE_METHOD_KIND ) ) {
            return kind ;
          } else if( method.equals( PHASE_METHOD_TOSTRING ) ) {
            return ToStringTools.getNiceName( phaseClass ) +
                ( featureMapper.featureInterfaces.isEmpty() ? "" :
                    "+" + joinInterfaceNames( featureMapper ) ) +
                '{' + asString( keyValuePairs, false ) + '}'  ;
          } else if( method.equals( PHASE_METHOD_HASHCODE ) ) {
            return keyValuePairs.hashCode() ;
          } else if( method.equals( PHASE_METHOD_EQUALS ) ) {
            final Object that = args[ 0 ] ;
            return isEqual( that, kind, keyValuePairs ) ;
          } else if( implementsInterface( method.getDeclaringClass(), Phase.class ) ) {
            final Key key = KEY_METHOD_MAP.get( kind ).inverse().get( method ) ;
            return keyValuePairs.get( key ) ;
          } else {
            final Object featureImplementor = featureMapper.implementor( method ) ;
            if( featureImplementor != null ) {
              return method.invoke( featureImplementor, args ) ;
            }
          }
          throw new DeclarationError( "Should not happen" ) ;
        }
    ) ;
    //noinspection unchecked
    return ( PHASE ) proxyInstance ;
  }

  private static String joinInterfaceNames( final FeatureMapper featureMapper ) {
    return Joiner.on( ',' ).join(
        Iterables.transform(
            featureMapper.featureInterfaces, Class::getSimpleName ) );
  }

  private static boolean isEqual(
      final Object other,
      final Kind thisKind,
      final ImmutableMap<Key, Object> keyValuePairs
  ) {
    if( other == null ) {
      return false ;
    } else if( other instanceof Phase ) {
      final Phase that = ( Phase ) other ;
      return that.kind() == thisKind && that.keyValuePairs().equals( keyValuePairs ) ;
    } else {
      return false ;
    }
  }

  private static Object asString(
      final ImmutableMap< Key, Object > keyValuePairs,
      final boolean urlEncode
  ) {
    final StringBuilder stringBuilder = new StringBuilder() ;
    for( final Map.Entry< Key, Object > entry : keyValuePairs.entrySet() ) {
      writeKeyValuePair( stringBuilder, entry.getKey(), entry.getValue(), urlEncode ) ;
    }
    return stringBuilder.toString() ;
  }

  private static void writeKeyValuePair(
      final StringBuilder builder,
      final Key key,
      final Object value,
      final boolean urlEncode
  ) {
    builder.append( key ) ;
    builder.append( '=' ) ;
    if( urlEncode ) {
      builder.append( value == null ? '!' : urlencodeQuiet( key.encode( value ) ) ) ;
    } else {
      if( value != null ) {
        builder.append( value.toString() ) ;
      }
    }
    builder.append( ';' ) ;
  }

  private static String urlencodeQuiet( final String value ) {
    final String urlEncoded ;
    try {
      urlEncoded = URLEncoder.encode( value, Charsets.UTF_8.name() ) ;
    } catch( final UnsupportedEncodingException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
    return urlEncoded ;
  }

  private static String urldecodeQuiet( final String urlEncoded ) {
    try {
      return URLDecoder.decode( urlEncoded, Charsets.UTF_8.name() ) ;
    } catch( final UnsupportedEncodingException e ) {
      throw new RuntimeException( "Should not happen", e ) ;
    }
  }


  /**
   * Names must match method names.
   * Made public only for tests.
   */
  public enum Key {
    LOGIN(),
    PASSWORD(),
    SECONDARY_TOKEN() {
      @Override
      String encode( final Object secondaryToken ) {
        return ( ( SecondaryToken ) secondaryToken ).asString() ;
      }
      @Override
      Object decode( final String string ) {
        return new SecondaryToken( string ) ;
      }
    },
    SESSION_IDENTIFIER() {
      @Override
      String encode( final Object sessionIdentifier ) {
        return ( ( SessionIdentifier ) sessionIdentifier ).asString() ;
      }
      @Override
      Object decode( final String string ) {
        return new SessionIdentifier( string ) ;
      }
    },
    SECONDARY_CODE() {
      @Override
      String encode( final Object secondaryCode ) {
        return ( ( SecondaryCode ) secondaryCode ).asString() ;
      }
      @Override
      Object decode( final String string ) {
        return new SecondaryCode( string ) ;
      }
    },
    SIGNON_FAILURE_NOTICE() {
      @Override
      String encode( final Object value ) {
        final SignonFailureNotice notice = ( SignonFailureNotice ) value ;
        return notice.kind + ( notice.hasMessage() ? ' ' + notice.message : "" ) ;
      }

      @Override
      Object decode( final String string ) throws DecodeException {
        final int firstSpaceLocation = string.indexOf( ' ' ) ;
        final String signonFailureAsString ;
        final String message ;
        if( firstSpaceLocation > 0 ) {
          signonFailureAsString = string.substring( 0, firstSpaceLocation ) ;
          message = string.substring( firstSpaceLocation + 1 ) ;
        } else {
          signonFailureAsString = string ;
          message = null ;
        }
        final SignonFailure signonFailure = EnumTools.fromNameLenient(
            SignonFailure.values(), signonFailureAsString ) ;
        if( signonFailure == null ) {
          throw new DecodeException( "Unsupported: '" + signonFailureAsString + "'" ) ;
        }
        if( message == null ) {
          return new SignonFailureNotice( signonFailure ) ;
        } else {
          return new SignonFailureNotice( signonFailure, message ) ;
        }
      }
    },
    ;

    String encode( final Object value ) {
      return ( String ) value ;
    }

    Object decode( final String string ) throws DecodeException {
      return string ;
    }
  }


  private static final ImmutableClassToInstanceMap< Object > EMPTY_FEATURE_MAP =
      ImmutableClassToInstanceMap.copyOf( ImmutableMap.of() ) ;

  public static final FeatureMapper EMPTY_FEATURE_MAPPER = new FeatureMapper( EMPTY_FEATURE_MAP ) ;

}
