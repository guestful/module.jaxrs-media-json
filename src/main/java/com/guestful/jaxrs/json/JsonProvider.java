/**
 * Copyright (C) 2013 Guestful (info@guestful.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guestful.jaxrs.json;

import com.guestful.json.JsonMapper;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JsonProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {

    private static final Set<Class<?>> UNTOUCHABLES = new HashSet<Class<?>>(asList(
        InputStream.class,
        Reader.class,
        OutputStream.class,
        Writer.class,
        byte[].class,
        char[].class,
        String.class,
        StreamingOutput.class,
        Response.class
    ));

    private static final List<Class<?>> UNREADABLES = asList(
        InputStream.class,
        Reader.class
    );

    private static final List<Class<?>> UNWRITABLES = asList(
        OutputStream.class,
        Writer.class,
        StreamingOutput.class,
        Response.class
    );

    private final Set<Class<?>> ignoredTypes = new HashSet<>();
    private final JsonMapper mapper;

    public JsonProvider(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public JsonProvider ignore(Class<?>... types) {
        Collections.addAll(ignoredTypes, types);
        return this;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {

        if (!isJsonType(mediaType)) return false;

        /* Ok: looks like we must weed out some core types here; ones that
         * make no sense to try to bind from JSON:
         */
        if (UNTOUCHABLES.contains(type)) return false;

        // and there are some other abstract/interface types to exclude too:
        if (UNREADABLES.stream().filter(c -> c.isAssignableFrom(type)).findFirst().isPresent()) return false;

        // as well as possible custom exclusions
        if (ignoredTypes.contains(type)) return false;

        return true;
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        return mapper.fromJson(entityStream, getCharset(mediaType), type);
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {

        if (!isJsonType(mediaType)) return false;

        /* Ok: looks like we must weed out some core types here; ones that
         * make no sense to try to bind from JSON:
         */
        if (UNTOUCHABLES.contains(type)) return false;

        // and there are some other abstract/interface types to exclude too:
        if (UNWRITABLES.stream().filter(c -> c.isAssignableFrom(type)).findFirst().isPresent()) return false;

        // as well as possible custom exclusions
        if (ignoredTypes.contains(type)) return false;

        return true;
    }

    @Override
    public long getSize(Object o, Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        try {
            mapper.toJson(o, entityStream, getCharset(mediaType));
        } catch (EOFException ignored) {
            // do nothing: output stream closed by clients
        } catch (IOException e) {
            if(e.getMessage() == null || !e.getMessage().contains("Broken pipe")) {
                throw e;
            }
            // do nothing: output stream closed by clients
        }
    }

    protected boolean isJsonType(MediaType mediaType) {
        /* As suggested by Stephen D, there are 2 ways to check: either
         * being as inclusive as possible (if subtype is "json"), or
         * exclusive (major type "application", minor type "json").
         * Let's start with inclusive one, hard to know which major
         * types we should cover aside from "application".
         */
        if (mediaType != null) {
            // Ok: there are also "xxx+json" subtypes, which count as well
            String subtype = mediaType.getSubtype();
            return "json".equalsIgnoreCase(subtype) || subtype.endsWith("+json");
        }
        /* Not sure if this can happen; but it seems reasonable
         * that we can at least produce json without media type?
         */
        return true;
    }

    private static Charset getCharset(MediaType m) {
        String name = (m == null) ? null : m.getParameters().get(MediaType.CHARSET_PARAMETER);
        return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
    }

}
