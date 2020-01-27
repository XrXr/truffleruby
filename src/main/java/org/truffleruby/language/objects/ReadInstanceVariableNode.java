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

import org.truffleruby.RubyContext;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class ReadInstanceVariableNode extends RubyContextSourceNode {

    private final String name;

    @Child private RubyNode receiver;
    @Child private ReadObjectFieldNode readNode;
    @Child private ReadObjectFieldNode readOrNullNode;

    private final ConditionProfile objectProfile = ConditionProfile.createBinaryProfile();

    public ReadInstanceVariableNode(String name, RubyNode receiver) {
        this.name = name;
        this.receiver = receiver;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);

        if (objectProfile.profile(receiverObject instanceof DynamicObject)) {
            return getReadNode().execute((DynamicObject) receiverObject, name, nil());
        } else {
            return nil();
        }
    }

    @Override
    public Object isDefined(VirtualFrame frame, RubyContext context) {
        final Object receiverObject = receiver.execute(frame);

        if (objectProfile.profile(receiverObject instanceof DynamicObject)) {
            if (getReadOrNullNode().execute((DynamicObject) receiverObject, name, null) == null) {
                return nil();
            } else {
                return coreStrings().INSTANCE_VARIABLE.createInstance();
            }
        } else {
            return false;
        }
    }

    private ReadObjectFieldNode getReadNode() {
        if (readNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readNode = insert(ReadObjectFieldNode.create());
        }

        return readNode;
    }

    private ReadObjectFieldNode getReadOrNullNode() {
        if (readOrNullNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readOrNullNode = insert(ReadObjectFieldNode.create());
        }

        return readOrNullNode;
    }

}
