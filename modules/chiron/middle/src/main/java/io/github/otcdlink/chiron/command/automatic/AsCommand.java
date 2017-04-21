package io.github.otcdlink.chiron.command.automatic;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mimics {@link io.github.otcdlink.chiron.command.Command.Description} but has
 * different {@code @Target}.
 */
@Target( ElementType.METHOD )
@Retention( RetentionPolicy.RUNTIME )
@Inherited
@Documented
public @interface AsCommand {
  boolean persist() default true ;
  boolean originAware() default true ;
  boolean tracked() default true ;

  Class< ? >[] moreInterfaces() default { } ;
}
