/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Casts a value into a boolean.
 */
@GenerateUncached
@NodeChild(value = "value", type = RubyNode.class)
public abstract class BooleanCastNode extends RubyBaseNode {

    public static BooleanCastNode create() {
        return BooleanCastNodeGen.create(null);
    }

    /** Execute with child node */
    public abstract boolean executeBoolean(VirtualFrame frame);

    /** Execute with given value */
    public abstract boolean executeToBoolean(Object value);

    @Specialization(guards = "isNil(context, nil)")
    protected boolean doNil(Object nil,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return false;
    }

    @Specialization
    protected boolean doBoolean(boolean value) {
        return value;
    }

    @Specialization
    protected boolean doInt(int value) {
        return true;
    }

    @Specialization
    protected boolean doLong(long value) {
        return true;
    }

    @Specialization
    protected boolean doFloat(double value) {
        return true;
    }

    @Specialization(guards = "!isNil(context, object)")
    protected boolean doBasicObject(DynamicObject object,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return true;
    }

    @Specialization(guards = "isForeignObject(object)", limit = "getCacheLimit()")
    protected boolean doForeignObject(
            TruffleObject object,
            @CachedLibrary("object") InteropLibrary objects,
            @Cached("createBinaryProfile()") ConditionProfile profile,
            @Cached BranchProfile failed) {
        if (profile.profile(objects.isBoolean(object))) {
            try {
                return objects.asBoolean(object);
            } catch (UnsupportedMessageException e) {
                failed.enter();
                // it concurrently stopped being boolean
                return true;
            }
        } else {
            return true;
        }
    }

    @Specialization(guards = { "!isTruffleObject(object)", "!isBoxedPrimitive(object)" })
    protected boolean doOther(Object object) {
        return true;
    }

    protected int getCacheLimit() {
        return RubyLanguage.getCurrentContext().getOptions().METHOD_LOOKUP_CACHE;
    }
}
