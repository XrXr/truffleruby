/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import org.truffleruby.language.RubyNode;
import org.truffleruby.language.locals.WriteFrameSlotNode;
import org.truffleruby.language.locals.WriteFrameSlotNodeGen;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FrameOnStackNode extends RubyNode {

    @Child private RubyNode child;
    @Child private WriteFrameSlotNode writeMarker;

    public FrameOnStackNode(RubyNode child, FrameSlot markerSlot) {
        this.child = child;
        writeMarker = WriteFrameSlotNodeGen.create(markerSlot);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final FrameOnStackMarker marker = new FrameOnStackMarker();

        writeMarker.executeWrite(frame, marker);

        try {
            return child.execute(frame);
        } finally {
            marker.setNoLongerOnStack();
        }
    }

}
