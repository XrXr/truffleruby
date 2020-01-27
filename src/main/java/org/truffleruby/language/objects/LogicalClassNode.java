/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyContextNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

@ImportStatic(ShapeCachingGuards.class)
public abstract class LogicalClassNode extends RubyContextNode {

    public static LogicalClassNode create() {
        return LogicalClassNodeGen.create();
    }

    public abstract DynamicObject executeLogicalClass(Object value);

    @Specialization(guards = "value")
    protected DynamicObject logicalClassTrue(boolean value) {
        return coreLibrary().trueClass;
    }

    @Specialization(guards = "!value")
    protected DynamicObject logicalClassFalse(boolean value) {
        return coreLibrary().falseClass;
    }

    @Specialization
    protected DynamicObject logicalClassInt(int value) {
        return coreLibrary().integerClass;
    }

    @Specialization
    protected DynamicObject logicalClassLong(long value) {
        return coreLibrary().integerClass;
    }

    @Specialization
    protected DynamicObject logicalClassDouble(double value) {
        return coreLibrary().floatClass;
    }

    @Specialization(
            guards = "object.getShape() == cachedShape",
            assumptions = "cachedShape.getValidAssumption()",
            limit = "getCacheLimit()")
    protected DynamicObject logicalClassCached(DynamicObject object,
            @Cached("object.getShape()") Shape cachedShape,
            @Cached("getLogicalClass(cachedShape)") DynamicObject logicalClass) {
        return logicalClass;
    }

    @Specialization(guards = "updateShape(object)")
    protected DynamicObject updateShapeAndLogicalClass(DynamicObject object) {
        return executeLogicalClass(object);
    }

    @Specialization(replaces = { "logicalClassCached", "updateShapeAndLogicalClass" })
    protected DynamicObject logicalClassUncached(DynamicObject object) {
        return Layouts.BASIC_OBJECT.getLogicalClass(object);
    }

    @Fallback
    protected DynamicObject logicalClassFallback(Object object) {
        return getContext().getCoreLibrary().getLogicalClass(object);
    }

    protected static DynamicObject getLogicalClass(Shape shape) {
        return Layouts.BASIC_OBJECT.getLogicalClass(shape.getObjectType());
    }

    protected int getCacheLimit() {
        return getContext().getOptions().CLASS_CACHE;
    }

}
