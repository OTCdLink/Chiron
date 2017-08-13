package com.otcdlink.chiron.toolbox;

public interface DynamicProperty<PROPERTY, VALUE> {
  VALUE valueOrDefault( VALUE defaultValue );

  PROPERTY reload();
}
