/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import static org.truffleruby.core.array.ArrayHelpers.getSize;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.objects.AllocateObjectNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayReadSliceNormalizedNode extends RubyContextNode {

    @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

    public abstract DynamicObject executeReadSlice(DynamicObject array, int index, int length);

    // Index out of bounds or negative length always gives you nil

    @Specialization(guards = "!indexInBounds(array, index)")
    protected DynamicObject readIndexOutOfBounds(DynamicObject array, int index, int length) {
        return nil();
    }

    @Specialization(guards = "!lengthPositive(length)")
    protected DynamicObject readNegativeLength(DynamicObject array, int index, int length) {
        return nil();
    }

    // Reading within bounds on an array with actual storage

    @Specialization(
            guards = {
                    "indexInBounds(array, index)",
                    "lengthPositive(length)",
                    "endInBounds(array, index, length)",
                    "strategy.matches(array)" },
            limit = "STORAGE_STRATEGIES")
    protected DynamicObject readInBounds(DynamicObject array, int index, int length,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayOperationNodes.ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode) {
        final Object store = extractRangeCopyOnWriteNode.execute(array, index, index + length);
        return createArrayOfSameClass(array, store, length);
    }

    // Reading beyond upper bounds on an array with actual storage needs clamping

    @Specialization(
            guards = {
                    "indexInBounds(array, index)",
                    "lengthPositive(length)",
                    "!endInBounds(array, index, length)",
                    "strategy.matches(array)" },
            limit = "STORAGE_STRATEGIES")
    protected DynamicObject readOutOfBounds(DynamicObject array, int index, int length,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayOperationNodes.ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode) {
        final int end = strategy.getSize(array);
        final Object store = extractRangeCopyOnWriteNode.execute(array, index, end);
        return createArrayOfSameClass(array, store, end - index);
    }

    // Guards

    protected DynamicObject createArrayOfSameClass(DynamicObject array, Object store, int size) {
        return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(array), store, size);
    }

    protected static boolean indexInBounds(DynamicObject array, int index) {
        return index >= 0 && index <= getSize(array);
    }

    protected static boolean lengthPositive(int length) {
        return length >= 0;
    }

    protected static boolean endInBounds(DynamicObject array, int index, int length) {
        return index + length <= getSize(array);
    }

}
