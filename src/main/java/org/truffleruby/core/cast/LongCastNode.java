/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

/** Casts a value into a long. */
@GenerateUncached
@ImportStatic(RubyGuards.class)
public abstract class LongCastNode extends RubyBaseNode {

    public static LongCastNode create() {
        return LongCastNodeGen.create();
    }

    public abstract long executeCastLong(Object value);

    @Specialization
    protected long doInt(int value) {
        return value;
    }

    @Specialization
    protected long doLong(long value) {
        return value;
    }

    @Specialization(guards = "!isBasicInteger(value)")
    protected long doBasicObject(
            Object value,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        throw new RaiseException(context, notAFixnum(context, value));
    }

    @TruffleBoundary
    private DynamicObject notAFixnum(RubyContext context, Object object) {
        return context.getCoreExceptions().typeErrorIsNotA(
                object.toString(),
                "Fixnum (fitting in long)",
                this);
    }

}
