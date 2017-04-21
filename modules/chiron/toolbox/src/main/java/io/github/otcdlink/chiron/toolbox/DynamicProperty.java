package io.github.otcdlink.chiron.toolbox;

public interface DynamicProperty<PROPERTY, VALUE> {
  VALUE valueOrDefault( VALUE defaultValue );

  PROPERTY reload();
}
