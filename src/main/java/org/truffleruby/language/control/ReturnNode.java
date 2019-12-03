/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public class ReturnNode extends RubyNode {

    private final ReturnID returnID;

    @Child private RubyNode value;

    public ReturnNode(ReturnID returnID, RubyNode value) {
        this.returnID = returnID;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        if (v.equals(Integer.valueOf(8424))) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            System.err.format("return node %s\n", returnID);
        }
        throw new ReturnException(returnID, v);
    }

}
