/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ReturnException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public class CatchReturnAsErrorNode extends RubyNode {

    @Child private RubyNode body;

    private final BranchProfile retryProfile = BranchProfile.create();

    public CatchReturnAsErrorNode(RubyNode body) {
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return body.execute(frame);
        } catch (ReturnException e) {
            retryProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().unexpectedReturn(this));
        }
    }

}
