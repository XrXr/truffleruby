/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.interop;

import org.truffleruby.Layouts;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.RubyBaseWithoutContextNode;
import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
@ImportStatic({ StringCachingGuards.class, StringOperations.class })
public abstract class ToJavaStringNode extends RubyBaseWithoutContextNode {

    // FIXME (pitr 12-Jun-2019): find a different way
    @NodeChild(value = "value", type = RubyNode.class)
    public static abstract class RubyNodeWrapperNode extends RubyNode {
        @Specialization
        protected Object call(Object value,
                @Cached ToJavaStringNode toJavaString) {
            return toJavaString.executeToJavaString(value);
        }
    }

    public static ToJavaStringNode create() {
        return ToJavaStringNodeGen.create();
    }

    public abstract String executeToJavaString(Object name);

    @Specialization(
            guards = { "isRubyString(value)", "equalsNode.execute(rope(value), cachedRope)" },
            limit = "getLimit()")
    protected String stringCached(DynamicObject value,
            @Cached("privatizeRope(value)") Rope cachedRope,
            @Cached("getString(value)") String convertedString,
            @Cached RopeNodes.EqualNode equalsNode) {
        return convertedString;
    }

    @Specialization(guards = "isRubyString(value)", replaces = "stringCached")
    protected String stringUncached(DynamicObject value,
            @Cached("createBinaryProfile()") ConditionProfile asciiOnlyProfile,
            @Cached RopeNodes.AsciiOnlyNode asciiOnlyNode,
            @Cached RopeNodes.BytesNode bytesNode) {
        final Rope rope = StringOperations.rope(value);
        final byte[] bytes = bytesNode.execute(rope);

        if (asciiOnlyProfile.profile(asciiOnlyNode.execute(rope))) {
            return RopeOperations.decodeAscii(bytes, 0, bytes.length);
        } else {
            return RopeOperations.decodeNonAscii(rope.getEncoding(), bytes, 0, bytes.length);
        }
    }

    @Specialization(
            guards = { "symbol == cachedSymbol", "isRubySymbol(cachedSymbol)" },
            limit = "getIdentityCacheLimit()")
    protected String symbolCached(DynamicObject symbol,
            @Cached("symbol") DynamicObject cachedSymbol,
            @Cached("symbolToString(symbol)") String convertedString) {
        return convertedString;
    }

    @Specialization(guards = "isRubySymbol(symbol)", replaces = "symbolCached")
    protected String symbolUncached(DynamicObject symbol) {
        return symbolToString(symbol);
    }

    protected String symbolToString(DynamicObject symbol) {
        return Layouts.SYMBOL.getString(symbol);
    }

    @Specialization(guards = "string == cachedString", limit = "getIdentityCacheLimit()")
    protected String javaStringCached(String string,
            @Cached("string") String cachedString) {
        return cachedString;
    }

    @Specialization(replaces = "javaStringCached")
    protected String javaStringUncached(String value) {
        return value;
    }

    protected int getLimit() {
        return RubyLanguage.getCurrentContext().getOptions().INTEROP_CONVERT_CACHE;
    }

    protected int getIdentityCacheLimit() {
        return RubyLanguage.getCurrentContext().getOptions().IDENTITY_CACHE;
    }

}
