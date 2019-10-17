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

import static org.truffleruby.core.array.ArrayHelpers.setSize;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayAppendManyNode extends RubyBaseNode {

    @Child private PropagateSharingNode propagateSharingNode = PropagateSharingNode.create();

    public abstract DynamicObject executeAppendMany(DynamicObject array, DynamicObject other);

    // Append of a compatible type

    @Specialization(
            guards = { "strategy.matches(array)", "otherStrategy.matches(other)", "generalized.equals(strategy)" },
            limit = "ARRAY_STRATEGIES")
    protected DynamicObject appendManySameType(DynamicObject array, DynamicObject other,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("of(other)") ArrayStrategy otherStrategy,
            @Cached("strategy.generalize(otherStrategy)") ArrayStrategy generalized,
            @Cached("strategy.capacityNode()") ArrayOperationNodes.ArrayCapacityNode capacityNode,
            @Cached("generalized.copyStoreNode()") ArrayOperationNodes.ArrayCopyStoreNode copyStoreNode,
            @Cached("otherStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
            @Cached("createBinaryProfile()") ConditionProfile extendProfile) {
        final int oldSize = strategy.getSize(array);
        final int otherSize = otherStrategy.getSize(other);
        final int newSize = oldSize + otherSize;
        final Object store = Layouts.ARRAY.getStore(array);
        final Object otherStore = Layouts.ARRAY.getStore(other);
        final int length = capacityNode.execute(store);

        propagateSharingNode.propagate(array, other);
        if (extendProfile.profile(newSize > length)) {
            final int capacity = ArrayUtils.capacity(getContext(), length, newSize);
            Object newStore = copyStoreNode.execute(store, capacity);
            copyToNode.execute(otherStore, newStore, 0, oldSize, otherSize);
            strategy.setStoreAndSize(array, newStore, newSize);
        } else {
            copyToNode.execute(otherStore, store, 0, oldSize, otherSize);
            setSize(array, newSize);
        }
        return array;
    }

    // Generalizations

    @Specialization(
            guards = { "strategy.matches(array)", "otherStrategy.matches(other)", "!generalized.equals(strategy)" },
            limit = "ARRAY_STRATEGIES")
    protected DynamicObject appendManyGeneralize(DynamicObject array, DynamicObject other,
            @Cached("of(array)") ArrayStrategy strategy,
            @Cached("of(other)") ArrayStrategy otherStrategy,
            @Cached("strategy.generalize(otherStrategy)") ArrayStrategy generalized,
            @Cached("generalized.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
            @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
            @Cached("otherStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode otherCopyToNode) {
        final int oldSize = strategy.getSize(array);
        final int otherSize = otherStrategy.getSize(other);
        final int newSize = oldSize + otherSize;
        final Object store = Layouts.ARRAY.getStore(array);
        final Object otherStore = Layouts.ARRAY.getStore(other);
        final Object newStore = newStoreNode.execute(newSize);

        propagateSharingNode.propagate(array, other);
        copyToNode.execute(store, newStore, 0, 0, oldSize);
        otherCopyToNode.execute(otherStore, newStore, 0, oldSize, otherSize);
        generalized.setStoreAndSize(array, newStore, newSize);
        return array;
    }

}
