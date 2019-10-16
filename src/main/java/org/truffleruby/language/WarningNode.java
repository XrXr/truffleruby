/*
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import com.oracle.truffle.api.source.SourceSection;

/**
 * Warns only if $VERBOSE is true.
 * Corresponds to Kernel#warn(message, uplevel: 1) if $VERBOSE, but in Java with a given SourceSection.
 */
public class WarningNode extends WarnNode {

    @Override
    public void warningMessage(SourceSection sourceSection, String message) {
        if (coreLibrary().isVerbose()) {
            callWarn(sourceSection, message);
        }
    }

}
