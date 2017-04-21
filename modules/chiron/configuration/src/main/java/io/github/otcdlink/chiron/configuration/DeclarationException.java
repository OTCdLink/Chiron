package io.github.otcdlink.chiron.configuration;

import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Thrown if there is something wrong in a {@link Configuration.Source} regarding the
 * definition enforced by a {@link Configuration.Factory}.
 */
public class DeclarationException extends ConfigurationException {

  public final Configuration.Factory factory ;
  public final ImmutableList< Validation.Bad > causes ;

  public DeclarationException(
      final Configuration.Factory factory,
      final ImmutableList< Validation.Bad > causes
  ) {
    super( "\n" + OnlineHelpTools.causesAsMultilineString( causes ) ) ;
    this.factory = checkNotNull( factory ) ;
    this.causes = checkNotNull( causes ) ;
  }

}
