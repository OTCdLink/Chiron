package com.otcdlink.chiron.upend.http.content.caching;

import com.otcdlink.chiron.upend.http.content.StaticContent;

import java.util.function.Function;

public interface StaticContentResolver< STATIC_CONTENT extends StaticContent>
    extends Function< String, STATIC_CONTENT >
{

}
