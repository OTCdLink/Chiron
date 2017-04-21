package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;

/**
 * Thrown if values read from {@link Configuration.Source}s infrige some validation rules.
 *
 * @see TemplateBasedFactory#create(Configuration.Source, Configuration.Source...)
 * @see TemplateBasedFactory#validate(Configuration)
 */
public class ValidationException extends DeclarationException {

  public ValidationException(
      final Configuration.Factory factory,
      final ImmutableList< Validation.Bad > causes
  ) {
    super( factory, causes ) ;
  }


}
