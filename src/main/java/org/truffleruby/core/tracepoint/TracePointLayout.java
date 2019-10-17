/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.tracepoint;

import org.truffleruby.core.basicobject.BasicObjectLayout;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Nullable;

@Layout
public interface TracePointLayout extends BasicObjectLayout {

    DynamicObjectFactory createTracePointShape(DynamicObject logicalClass,
            DynamicObject metaClass);

    Object[] build(
            @Nullable TracePointEvent[] events,
            @Nullable DynamicObject event,
            @Nullable DynamicObject path,
            int line,
            @Nullable DynamicObject binding,
            @Nullable DynamicObject proc,
            boolean insideProc);

    boolean isTracePoint(DynamicObject object);

    TracePointEvent[] getEvents(DynamicObject object);

    void setEvents(DynamicObject object, TracePointEvent[] value);

    DynamicObject getEvent(DynamicObject object);

    void setEvent(DynamicObject object, DynamicObject value);

    DynamicObject getPath(DynamicObject object);

    void setPath(DynamicObject object, DynamicObject value);

    int getLine(DynamicObject object);

    void setLine(DynamicObject object, int value);

    DynamicObject getBinding(DynamicObject object);

    void setBinding(DynamicObject object, DynamicObject value);

    DynamicObject getProc(DynamicObject object);

    void setProc(DynamicObject object, DynamicObject value);

    boolean getInsideProc(DynamicObject object);

    void setInsideProc(DynamicObject object, boolean value);

}
