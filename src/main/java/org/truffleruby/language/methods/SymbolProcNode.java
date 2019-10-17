/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;

public class SymbolProcNode extends RubyNode {

    private final String symbol;
    private final BranchProfile noReceiverProfile = BranchProfile.create();

    @Child private CallDispatchHeadNode callNode;

    public SymbolProcNode(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // Not using CheckArityNode as the message is different and arity is reported as -1
        final int given = RubyArguments.getArgumentsCount(frame);
        if (given == 0) {
            noReceiverProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().argumentError("no receiver given", this));
        }

        final Object receiver = RubyArguments.getArgument(frame, 0);
        final Object[] arguments = ArrayUtils.extractRange(RubyArguments.getArguments(frame), 1, given);
        final DynamicObject block = RubyArguments.getBlock(frame);

        return getCallNode().dispatch(frame, receiver, symbol, block, arguments);
    }

    private CallDispatchHeadNode getCallNode() {
        if (callNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callNode = insert(CallDispatchHeadNode.createPublic());
        }

        return callNode;
    }

}
