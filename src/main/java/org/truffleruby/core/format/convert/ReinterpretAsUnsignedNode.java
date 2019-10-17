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

import java.math.BigInteger;

import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.MissingValue;
import org.truffleruby.core.numeric.FixnumOrBignumNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@NodeChild("value")
public abstract class ReinterpretAsUnsignedNode extends FormatNode {

    @Child private FixnumOrBignumNode fixnumOrBignumNode;

    @Specialization
    protected MissingValue asUnsigned(MissingValue missingValue) {
        return missingValue;
    }

    @Specialization(guards = "isNil(nil)")
    protected DynamicObject asUnsigned(DynamicObject nil) {
        return nil;
    }

    @Specialization
    protected int asUnsigned(short value) {
        return value & 0xffff;
    }

    @Specialization
    protected long asUnsigned(int value) {
        return value & 0xffffffffL;
    }

    @Specialization
    protected Object asUnsigned(long value) {
        if (fixnumOrBignumNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            fixnumOrBignumNode = insert(new FixnumOrBignumNode());
        }

        return fixnumOrBignumNode.fixnumOrBignum(asUnsignedBigInteger(value));
    }

    private static final long UNSIGNED_LONG_MASK = 0x7fffffffffffffffL;

    @CompilerDirectives.TruffleBoundary
    private BigInteger asUnsignedBigInteger(long value) {
        // TODO CS 28-Mar-16 can't we work out if it would fit into a long, and not create a BigInteger?

        BigInteger bigIntegerValue = BigInteger.valueOf(value & UNSIGNED_LONG_MASK);

        if (value < 0) {
            bigIntegerValue = bigIntegerValue.setBit(Long.SIZE - 1);
        }

        return bigIntegerValue;
    }

}
