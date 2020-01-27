/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DoesRespondDispatchHeadNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class ReadUserKeywordsHashNode extends RubyContextSourceNode {

    private final int minArgumentCount;

    @Child private DoesRespondDispatchHeadNode respondToToHashNode;
    @Child private CallDispatchHeadNode callToHashNode;

    private final ConditionProfile notEnoughArgumentsProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile lastArgumentIsHashProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile respondsToToHashProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile convertedIsHashProfile = ConditionProfile.createBinaryProfile();

    public ReadUserKeywordsHashNode(int minArgumentCount) {
        this.minArgumentCount = minArgumentCount;
    }

    @Override
    public DynamicObject execute(VirtualFrame frame) {
        final int argumentCount = RubyArguments.getArgumentsCount(frame);

        if (notEnoughArgumentsProfile.profile(argumentCount <= minArgumentCount)) {
            return null;
        }

        final Object lastArgument = RubyArguments.getArgument(frame, argumentCount - 1);

        if (lastArgumentIsHashProfile.profile(RubyGuards.isRubyHash(lastArgument))) {
            return (DynamicObject) lastArgument;
        } else {
            return tryConvertToHash(frame, argumentCount, lastArgument);
        }
    }

    private DynamicObject tryConvertToHash(VirtualFrame frame, int argumentCount, Object lastArgument) {
        if (respondsToToHashProfile.profile(respondToToHash(frame, lastArgument))) {
            final Object converted = callToHash(frame, lastArgument);

            if (convertedIsHashProfile.profile(RubyGuards.isRubyHash(converted))) {
                RubyArguments.setArgument(frame, argumentCount - 1, converted);
                return (DynamicObject) converted;
            }
        }

        return null;
    }

    private boolean respondToToHash(VirtualFrame frame, Object lastArgument) {
        if (respondToToHashNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            respondToToHashNode = insert(DoesRespondDispatchHeadNode.create());
        }
        return respondToToHashNode.doesRespondTo(frame, "to_hash", lastArgument);
    }

    private Object callToHash(VirtualFrame frame, Object lastArgument) {
        if (callToHashNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callToHashNode = insert(CallDispatchHeadNode.createPrivate());
        }
        return callToHashNode.call(lastArgument, "to_hash");
    }

}
