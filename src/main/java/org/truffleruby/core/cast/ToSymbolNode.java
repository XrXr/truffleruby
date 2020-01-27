/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
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
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@GenerateUncached
public abstract class ToSymbolNode extends RubyBaseNode {

    public static ToSymbolNode create() {
        return ToSymbolNodeGen.create();
    }

    public abstract DynamicObject executeToSymbol(Object object);

    // TODO(CS): cache the conversion to a symbol? Or should the user do that themselves?

    @Specialization(guards = "isRubySymbol(symbol)")
    protected DynamicObject toSymbolSymbol(DynamicObject symbol) {
        return symbol;
    }

    @Specialization(guards = "isRubyString(string)")
    protected DynamicObject toSymbolString(DynamicObject string,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return context.getSymbolTable().getSymbol((StringOperations.rope(string)));
    }

    @Specialization
    protected DynamicObject toSymbol(String string,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return context.getSymbolTable().getSymbol(string);
    }

}
