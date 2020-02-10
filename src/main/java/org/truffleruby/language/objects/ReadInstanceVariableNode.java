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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.language.WarningNode;

public class ReadInstanceVariableNode extends RubyContextSourceNode {

    private final String name;

    @Child private RubyNode receiver;
    @Child private ReadObjectFieldNode readNode;
    @Child private ReadObjectFieldNode readOrNullNode;
    @Child private WarningNode warningNode;
    @Child private HasFieldNode hasFieldNode;

    private final ConditionProfile objectProfile = ConditionProfile.createBinaryProfile();

    private final boolean warnIfUndefined;

    public ReadInstanceVariableNode(String name, RubyNode receiver, boolean warnIfUndefined) {
        this.warnIfUndefined = warnIfUndefined;
        this.name = name;
        this.receiver = receiver;
    }

    public ReadInstanceVariableNode(String name, RubyNode receiver) {
        this(name, receiver, false);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object receiverObject = receiver.execute(frame);

        if (objectProfile.profile(receiverObject instanceof DynamicObject)) {
            if (warnIfUndefined && !hasFieldNode((DynamicObject) receiverObject)) {
                warnNotInitialized();
            }
            return getReadNode().execute((DynamicObject) receiverObject, name, nil());
        } else {
            return nil();
        }
    }

    private boolean hasFieldNode(DynamicObject receiverObject) {
        if (hasFieldNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hasFieldNode = insert(HasFieldNode.create());
        }
        return hasFieldNode.execute(receiverObject, name);
    }

    private void warnNotInitialized() {
        if (warningNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warningNode = insert(new WarningNode());
        }
        warningNode.warningMessage(getSourceSection(), getWarningMessage());
    }

    @TruffleBoundary
    private String getWarningMessage() {
        return String.format("instance variable %s not initialized", name);
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
