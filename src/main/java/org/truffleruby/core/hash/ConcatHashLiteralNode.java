/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.hash;

import java.util.ArrayList;
import java.util.List;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.DynamicObject;

public class ConcatHashLiteralNode extends RubyContextSourceNode {

    @Children private final RubyNode[] children;

    public ConcatHashLiteralNode(RubyNode[] children) {
        this.children = children;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return buildHash(executeChildren(frame));
    }

    @TruffleBoundary
    private Object buildHash(DynamicObject[] parts) {
        final List<KeyValue> keyValues = new ArrayList<>();

        for (int i = 0; i < parts.length; i++) {
            for (KeyValue keyValue : HashOperations.iterableKeyValues(parts[i])) {
                keyValues.add(keyValue);
            }
        }

        return BucketsStrategy.create(getContext(), keyValues, false);
    }

    @ExplodeLoop
    protected DynamicObject[] executeChildren(VirtualFrame frame) {
        DynamicObject[] values = new DynamicObject[children.length];
        for (int i = 0; i < children.length; i++) {
            DynamicObject hash = (DynamicObject) children[i].execute(frame);
            values[i] = hash;
        }
        return values;
    }

}
