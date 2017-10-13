package com.otcdlink.chiron.flow.journal;

import com.otcdlink.chiron.flow.journal.slicer.Slice;
import reactor.core.publisher.SynchronousSink;

public interface JournalReader< COMMAND  > {

  Iterable< Slice > sliceIterable() ;

  void decodeSlice(
      Slice slice,
      SynchronousSink< COMMAND > synchronousSink
  ) ;

}
