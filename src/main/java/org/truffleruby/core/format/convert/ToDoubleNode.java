/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.convert;

import org.truffleruby.core.format.FormatNode;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("value")
public abstract class ToDoubleNode extends FormatNode {

    public abstract double executeToDouble(VirtualFrame frame, Object object);

    @Specialization
    protected double toDouble(int value) {
        return value;
    }

    @Specialization
    protected double toDouble(long value) {
        return value;
    }

    @Specialization
    protected double toDouble(double value) {
        return value;
    }

}
