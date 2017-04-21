package io.github.otcdlink.chiron.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ByteProcessor;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static com.google.common.base.Preconditions.checkNotNull;

public final class BytebufTools {

  private BytebufTools() { }

  static final byte NULLITY_MARKER = '!' ;

  static final byte EXISTENCE_MARKER = '*' ;

  static final byte FIELD_END_MARKER = ' ' ;

  static final String BOOLEAN_TRUE = "T" ;

  static final String BOOLEAN_FALSE = "F" ;

  static final ByteProcessor FIND_FIELD_END_MARKER =
      new ByteProcessor.IndexOfProcessor( FIELD_END_MARKER ) ;


  public static BytebufCoat coat( final ByteBuf byteBuf ) {
    return new BytebufCoat( checkNotNull( byteBuf ) ) ;
  }

  /**
   * Helps recycling a single thread-local {@link BytebufCoat} instance.
   * <pre>
   * class MyStuff {
   *   final Coating coating = BytebufTools.threadLocalRecyclableCoating() ;
   *
   *   void deserialize( final ByteBuf byteBuf ) {
   *     try {
   *       deserialize( coating.coat( byteBuf ) ) ;
   *     } finally {
   *       coating.recycle() ;
   *     }
   *   }
   *
   *   void deserialize( final ReadableBytebuf readableBytebuf ) {
   *     // We are safer here.
   *   }
   * }
   * </pre>
   *
   *
   * We could do something nicer with lambdas but we should know the cost of creating one.
   * Or is this all overengineering with premature optimization?
   *
   */
  public static Coating threadLocalRecyclableCoating() {

    class LocalCoater implements Coating {
      private final BytebufCoat instance = new BytebufCoat() ;
      @Override
      public BytebufCoat coat( final ByteBuf byteBuf ) {
        instance.coat( byteBuf ) ;
        return instance ;
      }

      @Override
      public void recycle() {
        instance.recycle() ;
      }
    }

    final ThreadLocal< LocalCoater > recyclerThreadLocal = new ThreadLocal< LocalCoater > () {
      @Override
      protected LocalCoater initialValue() {
        return new LocalCoater() ;
      }
    } ;

    //noinspection Convert2Lambda
    return new Coating() {
      @Override
      public BytebufCoat coat( final ByteBuf byteBuf ) {
        final LocalCoater localRecycler = localCoaterSafe() ;
        return localRecycler.coat( byteBuf ) ;
      }

      @Override
      public void recycle() {
        localCoaterSafe().recycle() ;
      }

      private LocalCoater localCoaterSafe() {
        return recyclerThreadLocal.get() ;
      }
    } ;

  }

  /**
   * Primitive and unsafe recycling mechanism for short-lived objects in a very narrow
   * reference scope.
   */
  public interface Coating {
    BytebufCoat coat( ByteBuf byteBuf ) ;
    void recycle() ;
  }

  public static final DateTimeFormatter LOCALDATE_FORMATTER =
      DateTimeFormat.forPattern( "YYYYMMdd" ) ;


  public static String fullDump( final ByteBuf byteBuf ) {
    return ByteBufUtil.prettyHexDump( byteBuf, 0, byteBuf.writerIndex() ) ;
  }
}
