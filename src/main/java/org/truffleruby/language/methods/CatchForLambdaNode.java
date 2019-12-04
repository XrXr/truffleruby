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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.SourceIndexLength;
import org.truffleruby.language.control.BreakException;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.NextException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoException;
import org.truffleruby.language.control.RetryException;
import org.truffleruby.language.control.ReturnException;
import org.truffleruby.language.control.ReturnID;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class CatchForLambdaNode extends RubyNode {
    public static ReturnID theonetheone;

    private final ReturnID returnID;
    private final BreakID breakID;

    @Child private RubyNode body;

    private final ConditionProfile matchingReturnProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile matchingBreakProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile retryProfile = BranchProfile.create();
    private final BranchProfile redoProfile = BranchProfile.create();
    private final BranchProfile nextProfile = BranchProfile.create();

    public CatchForLambdaNode(ReturnID returnID, BreakID breakID, RubyNode body) {
        this.returnID = returnID;
        this.breakID = breakID;
        this.body = body;
    }

//    @Override
//    public void unsafeSetSourceSection(SourceSection ss) {
//        super.unsafeSetSourceSection(ss);
//        if (ss != null && ss.getSource() != null && "main_boot_source".equals(ss.getSource().getName())) {
//            new Exception().printStackTrace();
//            System.err.format("why are we here, just to suffer? %s %s %s body is %s\n", System.identityHashCode(this), ss, returnID, body);
//        }
//    }
//
//    @Override
//    public void unsafeSetSourceSection(SourceIndexLength si) {
//        super.unsafeSetSourceSection(si);
//        if (si.getCharIndex() == 0) {
//            System.err.format("si  are we here just to suffer? %s %s %s\n", System.identityHashCode(this), getSourceSection(), returnID);
//        }
//    }
//
//    @Override
//    public Node copy() {
//        Node superResult = super.copy();
//        SourceSection ss = getSourceSection();
//        if (ss != null && ss.getSource() != null && ("main_boot_source".equals(ss.getSource().getName()) || ss.getCharIndex() == 9498)) {
//            System.err.format("%s cloned into %s\n", System.identityHashCode(this), System.identityHashCode(superResult));
//        }
//        return superResult;
//    }

    @Override
    public Object execute(VirtualFrame frame) {
        // I set this up such that when returnID == theonetheone, it should not be possible to exit from this method without
        // one of the debug logs. However, on the bug reproduction setup, none of them trigger.
        /*  Here is a sample output
ling ling is my hero 60.
"start"
hwerwerwer
org.truffleruby.language.control.ReturnID@14f40030 at parse time it's 1137210118
"pre narrow2"
test/test_helper.rb:9:in `<main>'
haphap
return node org.truffleruby.language.control.ReturnID@14f40030
org.truffleruby.language.control.ReturnID@14f40030 (== theonetheone) exited ret = null
offending SourceSection(source=main_boot_source [1:1 - 1:60], index=0, length=60, characters=-> kind, to_execute { Truffle::Boot.main(kind, to_execute) }) 357908503 org.truffleruby.language.control.ReturnID@11a0e10, body: SequenceNode@3f8c91c5
org.graalvm.polyglot.PolyglotException: org.truffleruby.language.control.ReturnException
Original Internal Error:
org.truffleruby.language.control.ReturnException
          It doesn't add up and it looks like the program exited the while loop without respecting any of control paths
         */
        ReturnID localSpecificReturn = theonetheone;
        Object ret = null;
        int out = 0;
//        if (localSpecificReturn == returnID) { // *********** if you uncomment this block, the bug goes away!
//            System.err.format("executing %s\n", returnID);
//        }

        try {
            while (true) {
                try {
                    out = 1;
                    ret = body.execute(frame);
                    return ret;  // if it goes out here, ret in the finally block should not be null, but it is
                } catch (ReturnException e) {
                    out = 2;

                    if (matchingReturnProfile.profile(e.getReturnID() == returnID)) {
                        return e.getValue();
                    } else {
                        if (e.getValue().equals(new Integer(8424))) {
                            System.err.format("offending %s %s %s, body: %s\n", getSourceSection(), System.identityHashCode(this), returnID, body);
                        }
                        throw e;
                    }
                } catch (RetryException e) {
                    out = 3;
                    retryProfile.enter();

                    throw new RaiseException(getContext(), coreExceptions().syntaxErrorInvalidRetry(this));
                } catch (RedoException e) {
                    out = 4;
                    redoProfile.enter();
                    getContext().getSafepointManager().poll(this);
                    continue; // doesn't exit the method, no log
                } catch (NextException e) {
                    out = 6;

                    nextProfile.enter();
                    return e.getResult();
                } catch (BreakException e) {
                    out = 7;

                    if (matchingBreakProfile.profile(e.getBreakID() == breakID)) {
                        return e.getResult();
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    out = 8;
                    throw e;
                }
            }
        } finally {
            if (localSpecificReturn == returnID) {
                System.err.format("%s (== theonetheone) exited ret = %s out: %d \n", returnID, ret, out);
            }
        }
    }

}
