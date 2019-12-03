/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.control;

import com.oracle.truffle.api.nodes.ControlFlowException;

public final class NonLocalReturnException extends ControlFlowException {

    private static final long serialVersionUID = -45053969587014940L;

    private final NonLocalReturnID returnID;
    private final Object value;

    public NonLocalReturnException(NonLocalReturnID returnID, Object value) {
        this.returnID = returnID;
        this.value = value;
    }

    public NonLocalReturnID getReturnID() {
        return returnID;
    }

    public Object getValue() {
        return value;
    }

}
