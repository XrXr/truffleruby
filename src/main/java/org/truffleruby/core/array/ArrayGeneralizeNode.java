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

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayGeneralizeNode extends RubyBaseNode {

    public static ArrayGeneralizeNode create() {
        return ArrayGeneralizeNodeGen.create();
    }

    public abstract Object[] executeGeneralize(DynamicObject array, int requiredCapacity);

    @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
    protected Object[] generalize(DynamicObject array, int requiredCapacity,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("strategy.capacityNode()") ArrayOperationNodes.ArrayCapacityNode capacityNode,
            @Cached("strategy.boxedCopyNode()") ArrayOperationNodes.ArrayBoxedCopyNode boxedCopyNode,
            @Cached("createCountingProfile()") ConditionProfile extendProfile) {
        assert !ArrayGuards.isObjectArray(array);
        final Object store = Layouts.ARRAY.getStore(array);
        final int capacity;
        final int length = capacityNode.execute(store);
        if (extendProfile.profile(length < requiredCapacity)) {
            capacity = ArrayUtils.capacity(getContext(), length, requiredCapacity);
        } else {
            capacity = length;
        }
        final Object[] newStore = boxedCopyNode.execute(store, capacity);
        strategy.setStore(array, newStore);
        return newStore;
    }

}
