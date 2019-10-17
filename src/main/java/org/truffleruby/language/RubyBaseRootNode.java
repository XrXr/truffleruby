/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public abstract class RubyBaseRootNode extends RootNode {

    private final SourceSection sourceSection;

    public RubyBaseRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, SourceSection sourceSection) {
        super(language, frameDescriptor);
        this.sourceSection = sourceSection;
    }

    @Override
    public final SourceSection getSourceSection() {
        return sourceSection;
    }

    // NOTE (eregon, 11 Jan 2019): TruffleStackTrace calls this on the fast path, so it should constant-fold.
    @Override
    public boolean isInternal() {
        if (sourceSection != null) {
            return sourceSection.getSource().isInternal();
        }
        return false;
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return false;
    }

}
