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

import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.arguments.RubyArguments;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

public class GetCurrentVisibilityNode extends RubyContextNode {

    public Visibility getVisibility(VirtualFrame frame) {
        final Visibility visibility = RubyArguments.getDeclarationContext(frame).visibility;
        if (visibility != null) {
            return visibility;
        } else {
            return DeclarationContext.findVisibility(RubyArguments.getDeclarationFrame(frame));
        }
    }

    @TruffleBoundary
    public static Visibility getVisibilityFromNameAndFrame(String name, Frame frame) {
        if (ModuleOperations.isMethodPrivateFromName(name)) {
            return Visibility.PRIVATE;
        } else {
            return DeclarationContext.findVisibility(frame);
        }
    }

}
