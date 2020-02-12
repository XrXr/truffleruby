/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

/** Has both context and source methods. */
public abstract class RubyContextSourceNode extends RubyNode implements RubyNode.WithContext {

    private int sourceCharIndex = NO_SOURCE;
    private int sourceLength;
    private byte flags;

    @Override
    public Object isDefined(VirtualFrame frame, RubyContext context) {
        return RubyNode.defaultIsDefined(context, this);
    }

    @Override
    protected byte getFlags() {
        return flags;
    }

    @Override
    protected void setFlags(byte flags) {
        this.flags = flags;
    }

    @Override
    protected int getSourceCharIndex() {
        return sourceCharIndex;
    }

    @Override
    protected void setSourceCharIndex(int sourceCharIndex) {
        this.sourceCharIndex = sourceCharIndex;
    }

    @Override
    protected int getSourceLength() {
        return sourceLength;
    }

    @Override
    protected void setSourceLength(int sourceLength) {
        this.sourceLength = sourceLength;
    }

    // Context

    @CompilationFinal private RubyContext context;

    @Override
    public RubyContext getContext() {
        if (context == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            context = RubyLanguage.getCurrentContext();
        }

        return context;
    }
}
