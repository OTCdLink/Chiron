package com.otcdlink.chiron.wire;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class WireException extends Exception {

  public final Wire.Location location ;

  public WireException(
      final String message,
      final Exception cause,
      final Wire.Location location
  ) {
    super(
        message +
            ( location != null && location.meaningful() ? " (" + location.asString() + ")" : "" ),
        cause
    ) ;
    this.location = location ;
  }

  public static final class Generator {
    private final Supplier< Wire.Location > locationSupplier ;

    public Generator( Supplier< Wire.Location > locationSupplier ) {
      this.locationSupplier = checkNotNull( locationSupplier ) ;
    }

    public WireException throwWireException( final String message ) throws WireException {
      throw new WireException( message, null, locationSupplier.get() ) ;
    }

    public WireException throwWireException( final String message, final Exception cause )
        throws WireException
    {
      throw new WireException( message, cause, locationSupplier.get() ) ;
    }

    public WireException throwWireException( final Exception cause ) throws WireException {
      throw new WireException(
          cause.getMessage() == null ?
              "Propagating " + cause.getClass().getSimpleName() :
              cause.getMessage(),
          cause,
          locationSupplier.get()
      ) ;
    }

  }
}
