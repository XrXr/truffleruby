/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.PrimitiveNode;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayStrategy;
import org.truffleruby.core.basicobject.BasicObjectNodes.ReferenceEqualNode;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.kernel.KernelNodes.ToSNode;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsFrozenNode;
import org.truffleruby.language.objects.IsTaintedNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.ObjectIVarGetNode;
import org.truffleruby.language.objects.ObjectIVarSetNode;
import org.truffleruby.language.objects.PropertyFlags;
import org.truffleruby.language.objects.TaintNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreModule("Truffle::Type")
public abstract class TypeNodes {

    @Primitive(name = "object_kind_of?")
    public static abstract class ObjectKindOfNode extends PrimitiveArrayArgumentsNode {

        @Child private IsANode isANode = IsANode.create();

        @Specialization
        protected boolean objectKindOf(Object object, DynamicObject rubyClass) {
            return isANode.executeIsA(object, rubyClass);
        }

    }

    @Primitive(name = "object_respond_to?")
    public static abstract class ObjectRespondToNode extends PrimitiveArrayArgumentsNode {

        @Child private KernelNodes.RespondToNode respondToNode = KernelNodesFactory.RespondToNodeFactory
                .create(null, null, null);

        @Specialization
        protected boolean objectRespondTo(VirtualFrame frame, Object object, Object name, boolean includePrivate) {
            return respondToNode.executeDoesRespondTo(frame, object, name, includePrivate);
        }

    }

    @CoreMethod(names = "object_class", onSingleton = true, required = 1)
    public static abstract class ObjectClassNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNode.create();

        @Specialization
        protected DynamicObject objectClass(VirtualFrame frame, Object object) {
            return classNode.executeLogicalClass(object);
        }

    }

    @Primitive(name = "object_equal")
    public static abstract class ObjectEqualNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected boolean objectEqual(Object a, Object b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            return referenceEqualNode.executeReferenceEqual(a, b);
        }

    }

    @Primitive(name = "object_ivars")
    public abstract static class ObjectInstanceVariablesNode extends PrimitiveArrayArgumentsNode {

        public abstract DynamicObject executeGetIVars(Object self);

        @TruffleBoundary
        @Specialization(guards = { "!isNil(object)", "!isRubySymbol(object)" })
        protected DynamicObject instanceVariables(DynamicObject object) {
            Shape shape = object.getShape();
            List<String> names = new ArrayList<>();

            for (Property property : shape.getProperties()) {
                Object name = property.getKey();
                if (PropertyFlags.isDefined(property) && name instanceof String) {
                    names.add((String) name);
                }
            }

            final int size = names.size();
            final String[] sortedNames = names.toArray(new String[size]);
            Arrays.sort(sortedNames);

            final Object[] nameSymbols = new Object[size];
            for (int i = 0; i < sortedNames.length; i++) {
                nameSymbols[i] = getSymbol(sortedNames[i]);
            }

            return createArray(nameSymbols, size);
        }

        @Specialization
        protected DynamicObject instanceVariables(int object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization
        protected DynamicObject instanceVariables(long object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization
        protected DynamicObject instanceVariables(boolean object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(guards = "isNil(object)")
        protected DynamicObject instanceVariablesNil(DynamicObject object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(guards = "isRubySymbol(object)")
        protected DynamicObject instanceVariablesSymbol(DynamicObject object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Fallback
        protected DynamicObject instanceVariables(Object object) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

    }

    @Primitive(name = "object_ivar_defined?")
    public abstract static class ObjectIVarIsDefinedNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected Object ivarIsDefined(DynamicObject object, DynamicObject name) {
            final String ivar = Layouts.SYMBOL.getString(name);
            final Property property = object.getShape().getProperty(ivar);
            return PropertyFlags.isDefined(property);
        }

    }

    @Primitive(name = "object_ivar_get")
    public abstract static class ObjectIVarGetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object ivarGet(DynamicObject object, DynamicObject name,
                @Cached ObjectIVarGetNode iVarGetNode) {
            return iVarGetNode.executeIVarGet(object, Layouts.SYMBOL.getString(name));
        }
    }

    @Primitive(name = "object_ivar_set")
    public abstract static class ObjectIVarSetPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object ivarSet(DynamicObject object, DynamicObject name, Object value,
                @Cached ObjectIVarSetNode iVarSetNode) {
            return iVarSetNode.executeIVarSet(object, Layouts.SYMBOL.getString(name), value);
        }
    }

    // Those two primitives store the key as a Ruby object, so they have
    // different namespace than normal ivars which use java.lang.String.

    @Primitive(name = "object_hidden_var_get")
    public abstract static class ObjectHiddenVarGetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object objectHiddenVarGet(DynamicObject object, Object identifier,
                @Cached ObjectIVarGetNode iVarGetNode) {
            return iVarGetNode.executeIVarGet(object, identifier);
        }

        @Specialization(guards = "!isDynamicObject(object)")
        protected Object hiddenVariableGetPrimitive(Object object, Object identifier) {
            return nil();
        }
    }

    @Primitive(name = "object_hidden_var_set")
    public abstract static class ObjectHiddenVarSetNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object objectHiddenVarSet(DynamicObject object, Object identifier, Object value,
                @Cached ObjectIVarSetNode iVarSetNode) {
            return iVarSetNode.executeIVarSet(object, identifier, value);
        }
    }

    @Primitive(name = "object_can_contain_object")
    @ImportStatic(ArrayGuards.class)
    public abstract static class CanContainObjectNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "isRubyArray(array)", "strategy.matches(array)", "strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected boolean primitiveArray(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            return false;
        }

        @Specialization(
                guards = { "isRubyArray(array)", "strategy.matches(array)", "!strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected boolean objectArray(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            return true;
        }

        @Specialization(guards = "!isRubyArray(array)")
        protected boolean other(Object array) {
            return true;
        }

    }

    @CoreMethod(names = "rb_any_to_s", onSingleton = true, required = 1)
    public abstract static class ObjectToSNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected DynamicObject toS(Object obj,
                @Cached ToSNode kernelToSNode) {
            return kernelToSNode.executeToS(obj);
        }

    }

    @Primitive(name = "infect")
    public static abstract class InfectNode extends PrimitiveArrayArgumentsNode {

        @Child private IsTaintedNode isTaintedNode;
        @Child private TaintNode taintNode;

        @Specialization
        protected Object infect(Object host, Object source) {
            if (isTaintedNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTaintedNode = insert(IsTaintedNode.create());
            }

            if (isTaintedNode.executeIsTainted(source)) {
                // This lazy node allocation effectively gives us a branch profile

                if (taintNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    taintNode = insert(TaintNode.create());
                }

                taintNode.executeTaint(host);
            }

            return host;
        }

    }

    @CoreMethod(names = "module_name", onSingleton = true, required = 1)
    public static abstract class ModuleNameNode extends CoreMethodArrayArgumentsNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        protected DynamicObject moduleName(DynamicObject module) {
            final String name = Layouts.MODULE.getFields(module).getName();
            return makeStringNode.executeMake(name, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @Primitive(name = "double_to_float")
    public static abstract class DoubleToFloatNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected float doubleToFloat(double value) {
            return (float) value;
        }

    }

    @Primitive(name = "check_frozen")
    @NodeChild(value = "value", type = RubyNode.class)
    public static abstract class CheckFrozenNode extends PrimitiveNode {

        public static CheckFrozenNode create() {
            return create(null);
        }

        public static CheckFrozenNode create(RubyNode node) {
            return TypeNodesFactory.CheckFrozenNodeFactory.create(node);
        }

        public abstract void execute(Object object);

        @Specialization
        protected Object check(Object value,
                @Cached IsFrozenNode isFrozenNode,
                @Cached BranchProfile errorProfile) {

            if (isFrozenNode.execute(value)) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().frozenError(value, this));
            }

            return value;
        }
    }

    @Primitive(name = "undefined?")
    @NodeChild(value = "value", type = RubyNode.class)
    public static abstract class IsUndefinedNode extends PrimitiveNode {

        @Specialization
        protected boolean isUndefined(Object value) {
            return value == NotProvided.INSTANCE;
        }
    }
}
