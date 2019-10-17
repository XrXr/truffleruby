/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.method;

import org.truffleruby.core.basicobject.BasicObjectLayout;
import org.truffleruby.language.methods.InternalMethod;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.dsl.Layout;

@Layout
public interface MethodLayout extends BasicObjectLayout {

    DynamicObjectFactory createMethodShape(DynamicObject logicalClass,
            DynamicObject metaClass);

    DynamicObject createMethod(DynamicObjectFactory factory,
            Object receiver,
            InternalMethod method);

    boolean isMethod(DynamicObject object);

    boolean isMethod(Object object);

    boolean isMethod(ObjectType objectType);

    Object getReceiver(DynamicObject object);

    InternalMethod getMethod(DynamicObject object);

}
