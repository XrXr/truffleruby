/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.extra.ffi;

import org.truffleruby.core.basicobject.BasicObjectLayout;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;

@Layout
public interface PointerLayout extends BasicObjectLayout {

    DynamicObjectFactory createPointerShape(DynamicObject logicalClass,
            DynamicObject metaClass);

    DynamicObject createPointer(DynamicObjectFactory factory,
            Pointer pointer);

    boolean isPointer(DynamicObject object);

    Pointer getPointer(DynamicObject object);

    void setPointer(DynamicObject object, Pointer value);

}
