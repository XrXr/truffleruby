/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class TaintNode extends RubyBaseNode {

    @Child private IsFrozenNode isFrozenNode;
    @Child private IsTaintedNode isTaintedNode;

    public static TaintNode create() {
        return TaintNodeGen.create();
    }

    public abstract Object executeTaint(Object object);

    @Specialization
    protected Object taint(boolean object) {
        return object;
    }

    @Specialization
    protected Object taint(int object) {
        return object;
    }

    @Specialization
    protected Object taint(long object) {
        return object;
    }

    @Specialization
    protected Object taint(double object) {
        return object;
    }

    @Specialization(guards = "isRubySymbol(object) || isNil(object)")
    protected Object taintNilOrSymbol(DynamicObject object) {
        return object;
    }

    @Specialization(guards = { "!isRubySymbol(object)", "!isNil(object)" })
    protected Object taint(
            DynamicObject object,
            @Cached WriteObjectFieldNode writeTaintNode,
            @Cached BranchProfile errorProfile) {

        if (isTaintedNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isTaintedNode = insert(IsTaintedNode.create());
        }

        if (!isTaintedNode.executeIsTainted(object)) {
            if (isFrozenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isFrozenNode = insert(IsFrozenNode.create());
            }

            if (isFrozenNode.execute(object)) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().frozenError(object, this));
            }
        }

        writeTaintNode.write(object, Layouts.TAINTED_IDENTIFIER, true);
        return object;
    }
}
