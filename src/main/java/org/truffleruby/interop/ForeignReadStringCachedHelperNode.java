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

import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.dispatch.DoesRespondDispatchHeadNode;
import org.truffleruby.language.objects.ReadObjectFieldNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class ForeignReadStringCachedHelperNode extends RubyBaseNode {

    public static ForeignReadStringCachedHelperNode create() {
        return ForeignReadStringCachedHelperNodeGen.create();
    }

    protected final static String INDEX_METHOD_NAME = "[]";
    protected final static String FETCH_METHOD_NAME = "fetch";
    protected final static String METHOD_NAME = "method";

    public abstract Object executeStringCachedHelper(DynamicObject receiver, Object name, Object stringName,
            boolean isIVar) throws UnknownIdentifierException, InvalidArrayIndexException;

    protected static boolean arrayIndex(DynamicObject receiver, Object stringName) {
        return RubyGuards.isRubyArray(receiver) && stringName == null;
    }

    @Specialization(guards = "arrayIndex(receiver, stringName)")
    protected Object readArray(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached ForeignToRubyNode nameToRubyNode,
            @Cached(value = "createPrivate()") CallDispatchHeadNode dispatch,
            @CachedContext(RubyLanguage.class) RubyContext context,
            @Cached("createBinaryProfile()") ConditionProfile errorProfile) throws InvalidArrayIndexException {
        Object index = nameToRubyNode.executeConvert(name);
        try {
            return dispatch.call(receiver, FETCH_METHOD_NAME, index);
        } catch (RaiseException ex) {
            DynamicObject logicalClass = Layouts.BASIC_OBJECT.getLogicalClass(ex.getException());
            if (errorProfile.profile(logicalClass == context.getCoreLibrary().indexErrorClass)) {
                throw InvalidArrayIndexException.create((Long) index);
            } else {
                throw ex;
            }
        }
    }

    @Specialization(guards = "isRubyHash(receiver)")
    protected Object readArrayHash(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached ForeignToRubyNode nameToRubyNode,
            @Cached(value = "createPrivate()") CallDispatchHeadNode dispatch,
            @CachedContext(RubyLanguage.class) RubyContext context,
            @Cached ToSymbolNode toSymbolNode,
            @Cached("createBinaryProfile()") ConditionProfile errorProfile) throws UnknownIdentifierException {
        Object key = nameToRubyNode.executeConvert(name);
        try {
            return dispatch.call(receiver, FETCH_METHOD_NAME, key);
        } catch (RaiseException ex) {
            DynamicObject logicalClass = Layouts.BASIC_OBJECT.getLogicalClass(ex.getException());
            if (errorProfile.profile(logicalClass == context.getCoreLibrary().keyErrorClass)) {
                // try again with the key as a symbol
                // keeping this dirty since the whole hash-keys to members mapping has to be removed
                try {
                    return dispatch.call(receiver, FETCH_METHOD_NAME, toSymbolNode.executeToSymbol(name));
                } catch (RaiseException ex2) {
                    DynamicObject logicalClass2 = Layouts.BASIC_OBJECT.getLogicalClass(ex2.getException());
                    if (logicalClass2 == context.getCoreLibrary().keyErrorClass) {
                        throw UnknownIdentifierException.create((String) stringName);
                    } else {
                        throw ex2;
                    }
                }
            } else {
                throw ex;
            }
        }
    }

    @Specialization(guards = { "!isRubyHash(receiver)", "isIVar" })
    protected Object readInstanceVariable(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached ReadObjectFieldNode readObjectFieldNode) throws UnknownIdentifierException {
        Object result = readObjectFieldNode.execute(receiver, stringName, null);
        if (result != null) {
            return result;
        } else {
            throw UnknownIdentifierException.create((String) stringName);
        }
    }

    @Specialization(
            guards = {
                    "!isRubyArray(receiver)",
                    "!isRubyHash(receiver)",
                    "!isIVar",
                    "!isRubyProc(receiver)",
                    "!isRubyClass(receiver)",
                    "methodDefined(receiver, INDEX_METHOD_NAME, definedIndexNode)" })
    protected Object callIndex(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached DoesRespondDispatchHeadNode definedIndexNode,
            @Cached("createBinaryProfile()") ConditionProfile errorProfile,
            @CachedContext(RubyLanguage.class) RubyContext context,
            @Cached ForeignToRubyNode nameToRubyNode,
            @Cached(value = "createPrivate()") CallDispatchHeadNode dispatch) throws UnknownIdentifierException {
        try {
            return dispatch.call(receiver, INDEX_METHOD_NAME, nameToRubyNode.executeConvert(name));
        } catch (RaiseException ex) {
            // translate NameError to UnknownIdentifierException
            DynamicObject logicalClass = Layouts.BASIC_OBJECT.getLogicalClass(ex.getException());
            if (errorProfile.profile(logicalClass == context.getCoreLibrary().nameErrorClass)) {
                throw UnknownIdentifierException.create((String) stringName);
            } else {
                throw ex;
            }
        }
    }

    @Specialization(
            guards = {
                    "!isRubyHash(receiver)",
                    "!isIVar",
                    "noIndexMethod(definedIndexNode, receiver)",
                    "methodDefined(receiver, stringName, definedNode)" })
    protected Object getBoundMethod(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached DoesRespondDispatchHeadNode definedIndexNode,
            @Cached DoesRespondDispatchHeadNode definedNode,
            @Cached ForeignToRubyNode nameToRubyNode,
            @Cached(value = "createPrivate()") CallDispatchHeadNode dispatch) {
        return dispatch.call(receiver, METHOD_NAME, nameToRubyNode.executeConvert(name));
    }

    @Specialization(
            guards = {
                    "!isRubyHash(receiver)",
                    "!isIVar",
                    "noIndexMethod(definedIndexNode, receiver)",
                    "!methodDefined(receiver, stringName, definedNode)" })
    protected Object unknownIdentifier(
            DynamicObject receiver,
            Object name,
            Object stringName,
            boolean isIVar,
            @Cached DoesRespondDispatchHeadNode definedIndexNode,
            @Cached DoesRespondDispatchHeadNode definedNode) throws UnknownIdentifierException {
        throw UnknownIdentifierException.create(toString(name));
    }

    @TruffleBoundary
    private String toString(Object name) {
        return name.toString();
    }

    protected static boolean noIndexMethod(DoesRespondDispatchHeadNode definedIndexNode, DynamicObject receiver) {
        return !methodDefined(receiver, INDEX_METHOD_NAME, definedIndexNode) ||
                RubyGuards.isRubyArray(receiver) ||
                RubyGuards.isRubyProc(receiver) ||
                RubyGuards.isRubyClass(receiver);
    }

    protected static boolean methodDefined(DynamicObject receiver, Object stringName,
            DoesRespondDispatchHeadNode definedNode) {
        if (stringName == null) {
            return false;
        } else {
            return definedNode.doesRespondTo(null, stringName, receiver);
        }
    }
}
