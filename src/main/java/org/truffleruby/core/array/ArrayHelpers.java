/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;

import com.oracle.truffle.api.object.DynamicObject;

public abstract class ArrayHelpers {

    public static Object getStore(DynamicObject array) {
        return Layouts.ARRAY.getStore(array);
    }

    public static int getSize(DynamicObject array) {
        return Layouts.ARRAY.getSize(array);
    }

    public static void setStoreAndSize(DynamicObject array, Object store, int size) {
        Layouts.ARRAY.setStore(array, store);
        setSize(array, size);
    }

    /**
     * Sets the size of the given array
     *
     * Asserts that the size is valid for the current store of the array.
     * If setting both size and store, use setStoreAndSize or be sure to setStore before
     * setSize as this assertion may fail.
     * @param array
     * @param size
     */
    public static void setSize(DynamicObject array, int size) {
        assert ArrayOperations.getStoreCapacity(array) >= size;
        Layouts.ARRAY.setSize(array, size);
    }

    public static DynamicObject createArray(RubyContext context, Object store, int size) {
        return Layouts.ARRAY.createArray(context.getCoreLibrary().getArrayFactory(), store, size);
    }

    public static DynamicObject createArray(RubyContext context, Object[] store) {
        return createArray(context, store, store.length);
    }

    public static DynamicObject createEmptyArray(RubyContext context) {
        return Layouts.ARRAY.createArray(context.getCoreLibrary().getArrayFactory(), ArrayStrategy.NULL_ARRAY_STORE, 0);
    }

}
