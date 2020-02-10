/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

@ReportPolymorphism
@GenerateUncached
public abstract class ObjectIVarGetNode extends RubyBaseNode {

    public static ObjectIVarGetNode create() {
        return ObjectIVarGetNodeGen.create();
    }

    public abstract Object executeIVarGet(DynamicObject object, Object name, boolean checkName);

    public final Object executeIVarGet(DynamicObject object, Object name) {
        return executeIVarGet(object, name, false);
    }

    @Specialization(guards = { "name == cachedName", "checkName == cachedCheckName" }, limit = "getCacheLimit()")
    protected Object ivarGetCached(DynamicObject object, Object name, boolean checkName,
            @Cached("checkName") boolean cachedCheckName,
            @CachedContext(RubyLanguage.class) RubyContext context,
            @Cached("checkName(context, object, name, cachedCheckName)") Object cachedName,
            @Cached ReadObjectFieldNode readObjectFieldNode) {
        return readObjectFieldNode.execute(object, cachedName, context.getCoreLibrary().nil);
    }

    @TruffleBoundary
    @Specialization(replaces = "ivarGetCached")
    protected Object ivarGetUncached(DynamicObject object, Object name, boolean checkName,
            @CachedContext(RubyLanguage.class) RubyContext context) {
        return ReadObjectFieldNodeGen.getUncached().execute(
                object,
                checkName(context, object, name, checkName),
                context.getCoreLibrary().nil);
    }

    protected Object checkName(RubyContext context, DynamicObject object, Object name, boolean checkName) {
        return checkName(context, object, name, checkName, this);
    }

    static Object checkName(
            RubyContext context, DynamicObject object, Object name, boolean checkName, Node currentNode) {
        return checkName ? SymbolTable.checkInstanceVariableName(context, (String) name, object, currentNode) : name;
    }

    protected int getCacheLimit() {
        return RubyLanguage.getCurrentContext().getOptions().INSTANCE_VARIABLE_CACHE;
    }

}
