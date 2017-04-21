package io.github.otcdlink.chiron.upend.http.caching;

import java.util.function.Function;

public interface StaticContentResolver< STATIC_CONTENT extends StaticContent >
    extends Function< String, STATIC_CONTENT >
{

}
