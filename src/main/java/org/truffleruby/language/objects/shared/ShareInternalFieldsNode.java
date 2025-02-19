/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import org.truffleruby.Layouts;
import org.truffleruby.collections.BoundaryIterable;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.array.DelegatedArrayStorage;
import org.truffleruby.core.queue.UnsizedQueue;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.objects.ShapeCachingGuards;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;

/** Share the internal fields of an object, accessible by its Layout */
@ImportStatic({ ShapeCachingGuards.class, ArrayGuards.class })
public abstract class ShareInternalFieldsNode extends RubyContextNode {

    protected static final int CACHE_LIMIT = 8;

    protected final int depth;

    public ShareInternalFieldsNode(int depth) {
        this.depth = depth;
    }

    public abstract void executeShare(DynamicObject object);

    @Specialization(
            guards = { "array.getShape() == cachedShape", "isArrayShape(cachedShape)", "isObjectArray(array)" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void shareCachedObjectArray(DynamicObject array,
            @Cached("array.getShape()") Shape cachedShape,
            @Cached("createWriteBarrierNode()") WriteBarrierNode writeBarrierNode) {
        final int size = Layouts.ARRAY.getSize(array);
        final Object[] store = (Object[]) Layouts.ARRAY.getStore(array);
        for (int i = 0; i < size; i++) {
            writeBarrierNode.executeWriteBarrier(store[i]);
        }
    }

    @Specialization(
            guards = {
                    "array.getShape() == cachedShape",
                    "isArrayShape(cachedShape)",
                    "isDelegatedObjectArray(array)" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void shareCachedDelegatedArray(DynamicObject array,
            @Cached("array.getShape()") Shape cachedShape,
            @Cached("createWriteBarrierNode()") WriteBarrierNode writeBarrierNode) {
        final DelegatedArrayStorage delegated = (DelegatedArrayStorage) Layouts.ARRAY.getStore(array);
        final Object[] store = (Object[]) delegated.storage;
        for (int i = delegated.offset; i < delegated.offset + delegated.length; i++) {
            writeBarrierNode.executeWriteBarrier(store[i]);
        }
    }

    @Specialization(
            guards = {
                    "array.getShape() == cachedShape",
                    "isArrayShape(cachedShape)",
                    "!isObjectArray(array)",
                    "!isDelegatedObjectArray(array)" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void shareCachedOtherArray(DynamicObject array,
            @Cached("array.getShape()") Shape cachedShape) {
        /* null, int[], long[] or double[] storage */
        assert ArrayOperations.isPrimitiveStorage(array);
    }

    @Specialization(
            guards = { "object.getShape() == cachedShape", "isQueueShape(cachedShape)" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void shareCachedQueue(DynamicObject object,
            @Cached("object.getShape()") Shape cachedShape,
            @Cached("createBinaryProfile()") ConditionProfile profileEmpty,
            @Cached("createWriteBarrierNode()") WriteBarrierNode writeBarrierNode) {
        final UnsizedQueue queue = Layouts.QUEUE.getQueue(object);
        if (!profileEmpty.profile(queue.isEmpty())) {
            for (Object e : BoundaryIterable.wrap(queue.getContents())) {
                writeBarrierNode.executeWriteBarrier(e);
            }
        }
    }

    @Specialization(
            guards = { "object.getShape() == cachedShape", "isBasicObjectShape(cachedShape)", "!hasFinalizerRef" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void shareCachedBasicObject(DynamicObject object,
            @Cached("object.getShape()") Shape cachedShape,
            @Cached("hasFinalizerRefProperty(cachedShape)") boolean hasFinalizerRef) {
        /* No internal fields */
    }

    @Specialization(
            replaces = {
                    "shareCachedObjectArray",
                    "shareCachedDelegatedArray",
                    "shareCachedOtherArray",
                    "shareCachedQueue",
                    "shareCachedBasicObject" })
    protected void shareUncached(DynamicObject object) {
        SharedObjects.shareInternalFields(getContext(), object);
    }

    protected static boolean isDelegatedObjectArray(DynamicObject array) {
        final Object store = Layouts.ARRAY.getStore(array);
        return store instanceof DelegatedArrayStorage && ((DelegatedArrayStorage) store).hasObjectArrayStorage();
    }

    protected static boolean hasFinalizerRefProperty(Shape shape) {
        return shape.hasProperty(Layouts.FINALIZER_REF_IDENTIFIER);
    }

    protected WriteBarrierNode createWriteBarrierNode() {
        return WriteBarrierNodeGen.create(depth);
    }

}
