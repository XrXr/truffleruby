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
import org.truffleruby.language.control.LocalReturnException;
import org.truffleruby.language.control.DynamicReturnException;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RetryException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class CatchForMethodNode extends RubyNode {

    private final ReturnID returnID;

    @Child private RubyNode body;

    private final BranchProfile localReturnProfile = BranchProfile.create();
    private final ConditionProfile matchingReturnProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile retryProfile = BranchProfile.create();

    public CatchForMethodNode(ReturnID returnID, RubyNode body) {
        this.returnID = returnID;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return body.execute(frame);
        } catch (LocalReturnException e) {
            localReturnProfile.enter();
            return e.getValue();
        } catch (DynamicReturnException e) {
            if (matchingReturnProfile.profile(e.getReturnID() == returnID)) {
                return e.getValue();
            } else {
                throw e;
            }
        } catch (RetryException e) {
            retryProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().syntaxErrorInvalidRetry(this));
        }
    }

}
