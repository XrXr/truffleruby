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

import java.util.ArrayList;
import java.util.List;

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.interop.RubyObjectType;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.objects.ObjectGraph;
import org.truffleruby.language.objects.ShapeCachingGuards;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectLocation;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

/** Share the object and all that is reachable from it (see {@link ObjectGraph#getAdjacentObjects(DynamicObject)}. */
@ImportStatic(ShapeCachingGuards.class)
public abstract class ShareObjectNode extends RubyContextNode {

    protected static final int CACHE_LIMIT = 8;

    protected final int depth;

    public ShareObjectNode(int depth) {
        this.depth = depth;
    }

    public abstract void executeShare(DynamicObject object);

    @Specialization(
            guards = "object.getShape() == cachedShape",
            assumptions = { "cachedShape.getValidAssumption()", "sharedShape.getValidAssumption()" },
            limit = "CACHE_LIMIT")
    @ExplodeLoop
    protected void shareCached(DynamicObject object,
            @Cached("ensureSharedClasses(getContext(), object.getShape())") Shape cachedShape,
            @Cached("createShareInternalFieldsNode()") ShareInternalFieldsNode shareInternalFieldsNode,
            @Cached("createReadAndShareFieldNodes(getObjectProperties(cachedShape))") ReadAndShareFieldNode[] readAndShareFieldNodes,
            @Cached("createSharedShape(cachedShape)") Shape sharedShape) {
        // Mark the object as shared first to avoid recursion
        object.setShapeAndGrow(cachedShape, sharedShape);

        shareInternalFieldsNode.executeShare(object);

        for (ReadAndShareFieldNode readAndShareFieldNode : readAndShareFieldNodes) {
            readAndShareFieldNode.executeReadFieldAndShare(object);
        }

        assert allFieldsAreShared(object);
    }

    private boolean allFieldsAreShared(DynamicObject object) {
        for (DynamicObject value : ObjectGraph.getAdjacentObjects(object)) {
            assert SharedObjects.isShared(getContext(), value) : "unshared field in shared object: " + value;
        }

        return true;
    }

    @Specialization(guards = "updateShape(object)")
    protected void updateShapeAndShare(DynamicObject object) {
        executeShare(object);
    }

    @Specialization(replaces = { "shareCached", "updateShapeAndShare" })
    protected void shareUncached(DynamicObject object) {
        SharedObjects.writeBarrier(getContext(), object);
    }

    protected static Shape ensureSharedClasses(RubyContext context, Shape shape) {
        final ObjectType objectType = shape.getObjectType();
        if (objectType instanceof RubyObjectType) {
            SharedObjects.writeBarrier(context, Layouts.BASIC_OBJECT.getLogicalClass(objectType));
            SharedObjects.writeBarrier(context, Layouts.BASIC_OBJECT.getMetaClass(objectType));
        }
        return shape;
    }

    protected static List<Property> getObjectProperties(Shape shape) {
        final List<Property> objectProperties = new ArrayList<>();
        // User properties only, ShareInternalFieldsNode do the rest
        for (Property property : shape.getProperties()) {
            if (property.getLocation() instanceof ObjectLocation) {
                objectProperties.add(property);
            }
        }
        return objectProperties;
    }

    protected ShareInternalFieldsNode createShareInternalFieldsNode() {
        return ShareInternalFieldsNodeGen.create(depth);
    }

    protected ReadAndShareFieldNode[] createReadAndShareFieldNodes(List<Property> properties) {
        ReadAndShareFieldNode[] nodes = properties.size() == 0
                ? ReadAndShareFieldNode.EMPTY_ARRAY
                : new ReadAndShareFieldNode[properties.size()];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = ReadAndShareFieldNodeGen.create(properties.get(i), depth);
        }
        return nodes;
    }

    protected Shape createSharedShape(Shape cachedShape) {
        if (SharedObjects.isShared(getContext(), cachedShape)) {
            throw new UnsupportedOperationException(
                    "Thread-safety bug: the object is already shared. This means another thread marked the object as shared concurrently.");
        } else {
            return cachedShape.makeSharedShape();
        }
    }

}
