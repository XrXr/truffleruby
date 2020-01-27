/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.object.DynamicObject;

@GenerateUncached
@ImportStatic(StringCachingGuards.class)
public abstract class ForeignReadStringCachingHelperNode extends RubyBaseNode {

    public abstract Object executeStringCachingHelper(DynamicObject receiver, Object name)
            throws UnknownIdentifierException, InvalidArrayIndexException;

    @Specialization(guards = "isStringLike.executeIsStringLike(name)")
    protected Object cacheStringLikeAndForward(DynamicObject receiver, Object name,
            @Cached ToJavaStringNode toJavaStringNode,
            @Cached IsStringLikeNode isStringLike,
            @Cached ForeignReadStringCachedHelperNode nextHelper)
            throws UnknownIdentifierException, InvalidArrayIndexException {
        String nameAsJavaString = toJavaStringNode.executeToJavaString(name);
        boolean isIVar = isIVar(nameAsJavaString);
        return nextHelper.executeStringCachedHelper(receiver, name, nameAsJavaString, isIVar);
    }

    @Specialization(guards = "!isStringLike.executeIsStringLike(name)")
    protected Object indexObject(DynamicObject receiver, Object name,
            @Cached IsStringLikeNode isStringLike,
            @Cached ForeignReadStringCachedHelperNode nextHelper)
            throws UnknownIdentifierException, InvalidArrayIndexException {
        return nextHelper.executeStringCachedHelper(receiver, name, null, false);
    }

    protected boolean isIVar(String name) {
        return !name.isEmpty() && name.charAt(0) == '@';
    }

}
