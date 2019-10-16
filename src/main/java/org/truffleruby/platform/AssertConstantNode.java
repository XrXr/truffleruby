/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.platform;

import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild("value")
public abstract class AssertConstantNode extends RubyNode {

    @Specialization
    protected Object assertCompilationConstant(Object value) {
        if (!CompilerDirectives.isCompilationConstant(value)) {
            notConstantBoundary();
        }

        return value;
    }

    @TruffleBoundary
    private void notConstantBoundary() {
        throw new RaiseException(getContext(), coreExceptions().graalErrorAssertConstantNotConstant(this), true);
    }

}
