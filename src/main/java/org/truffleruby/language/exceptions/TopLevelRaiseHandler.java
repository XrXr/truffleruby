/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.exceptions;

import org.truffleruby.Layouts;
import org.truffleruby.core.cast.IntegerCastNode;
import org.truffleruby.core.kernel.AtExitManager;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.objects.ReadObjectFieldNodeGen;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;

public class TopLevelRaiseHandler extends RubyContextSourceNode {

    @Child private RubyNode body;
    @Child private IntegerCastNode integerCastNode;
    @Child private SetExceptionVariableNode setExceptionVariableNode;

    public TopLevelRaiseHandler(RubyNode body) {
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        int exitCode = 0;
        DynamicObject caughtException = null;

        try {
            body.execute(frame);
        } catch (RaiseException e) {
            DynamicObject rubyException = AtExitManager.handleAtExitException(getContext(), e);
            caughtException = rubyException;
            setLastException(frame, rubyException);
            exitCode = statusFromException(rubyException);
        } catch (ExitException e) {
            exitCode = e.getCode();
        } finally {
            final DynamicObject atExitException = getContext().getAtExitManager().runAtExitHooks();

            if (atExitException != null) {
                exitCode = statusFromException(atExitException);
            }

            if (caughtException != null) {
                handleSignalException(caughtException);
            }
        }

        return exitCode;
    }

    private int statusFromException(DynamicObject exception) {
        if (Layouts.BASIC_OBJECT.getLogicalClass(exception) == coreLibrary().systemExitClass) {
            return castToInt(ReadObjectFieldNodeGen.getUncached().execute(exception, "@status", null));
        } else {
            return 1;
        }
    }

    private int castToInt(Object value) {
        if (integerCastNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            integerCastNode = insert(IntegerCastNode.create());
        }

        return integerCastNode.executeCastInt(value);
    }

    public void setLastException(VirtualFrame frame, DynamicObject exception) {
        if (setExceptionVariableNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setExceptionVariableNode = insert(new SetExceptionVariableNode());
        }

        setExceptionVariableNode.setLastException(frame, exception);
    }

    private void handleSignalException(DynamicObject exception) {
        if (Layouts.BASIC_OBJECT.getLogicalClass(exception) == coreLibrary().signalExceptionClass) {
            // Calls raise(3) or no-op
            CallDispatchHeadNode.getUncached().call(exception, "reached_top_level");
        }
    }

}
