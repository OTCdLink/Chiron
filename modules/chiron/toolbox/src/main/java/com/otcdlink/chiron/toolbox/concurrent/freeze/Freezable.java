package com.otcdlink.chiron.toolbox.concurrent.freeze;

import java.util.function.Consumer;

public interface Freezable< THIS > {

  void freeze( Consumer< THIS > freezer ) ;

}
