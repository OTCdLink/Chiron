package com.otcdlink.chiron.testing.junit5;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ TYPE, METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Repeatable( WithSystemProperties.class )
@ExtendWith( WithSystemPropertiesExtension.class )
public @interface WithSystemProperty {
  String name() ;
  String value() ;
}
