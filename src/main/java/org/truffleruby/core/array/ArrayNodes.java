/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import static org.truffleruby.core.array.ArrayHelpers.getSize;
import static org.truffleruby.core.array.ArrayHelpers.getStore;
import static org.truffleruby.core.array.ArrayHelpers.setSize;
import static org.truffleruby.core.array.ArrayHelpers.setStoreAndSize;

import java.util.Arrays;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.array.ArrayEachIteratorNode.ArrayElementConsumerNode;
import org.truffleruby.core.array.ArrayNodesFactory.ReplaceNodeFactory;
import org.truffleruby.core.array.ArrayOperationNodes.ArrayExtractRangeCopyOnWriteNode;
import org.truffleruby.core.cast.CmpIntNode;
import org.truffleruby.core.cast.ToAryNode;
import org.truffleruby.core.cast.ToAryNodeGen;
import org.truffleruby.core.cast.ToIntNode;
import org.truffleruby.core.cast.ToIntNodeGen;
import org.truffleruby.core.cast.ToStrNodeGen;
import org.truffleruby.core.format.BytesResult;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.pack.PackCompiler;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqlNode;
import org.truffleruby.core.kernel.KernelNodes.SameOrEqualNode;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.kernel.KernelNodesFactory.SameOrEqlNodeFactory;
import org.truffleruby.core.numeric.FixnumLowerNodeGen;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.support.TypeNodes;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.language.objects.PropagateTaintNode;
import org.truffleruby.language.objects.TaintNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;
import org.truffleruby.language.yield.YieldNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;

@CoreModule(value = "Array", isClass = true)
public abstract class ArrayNodes {

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        protected DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

    }

    @CoreMethod(names = "+", required = 1)
    @NodeChild(value = "a", type = RubyNode.class)
    @NodeChild(value = "b", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class AddNode extends CoreMethodNode {

        @CreateCast("b")
        protected RubyNode coerceOtherToAry(RubyNode other) {
            return ToAryNodeGen.create(other);
        }

        // Same storage

        @Specialization(guards = { "strategy.matches(a)", "strategy.matches(b)" }, limit = "STORAGE_STRATEGIES")
        protected DynamicObject addSameType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode) {
            final int aSize = strategy.getSize(a);
            final int bSize = strategy.getSize(b);
            final int combinedSize = aSize + bSize;
            Object newStore = newStoreNode.execute(combinedSize);
            copyToNode.execute(Layouts.ARRAY.getStore(a), newStore, 0, 0, aSize);
            copyToNode.execute(Layouts.ARRAY.getStore(b), newStore, 0, aSize, bSize);
            return createArray(newStore, combinedSize);
        }

        // Generalizations

        @Specialization(
                guards = { "aStrategy.matches(a)", "bStrategy.matches(b)", "aStrategy != bStrategy" },
                limit = "ARRAY_STRATEGIES")
        protected DynamicObject addGeneralize(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy aStrategy,
                @Cached("of(b)") ArrayStrategy bStrategy,
                @Cached("aStrategy.generalize(bStrategy)") ArrayStrategy generalized,
                @Cached("aStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode aCopyToNode,
                @Cached("bStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode bCopyToNode,
                @Cached("generalized.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode) {
            final int aSize = aStrategy.getSize(a);
            final int bSize = bStrategy.getSize(b);
            final int combinedSize = aSize + bSize;
            Object newStore = newStoreNode.execute(combinedSize);
            aCopyToNode.execute(Layouts.ARRAY.getStore(a), newStore, 0, 0, aSize);
            bCopyToNode.execute(Layouts.ARRAY.getStore(b), newStore, 0, aSize, bSize);
            return createArray(newStore, combinedSize);
        }

    }

    @Primitive(name = "array_mul", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class MulNode extends PrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();
        @Child private PropagateTaintNode propagateTaintNode = PropagateTaintNode.create();

        @Specialization(
                guards = { "!isEmptyArray(array)", "strategy.matches(array)", "count >= 0" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject mulOther(DynamicObject array, int count,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode) {

            final int size = strategy.getSize(array);
            final int newSize;
            try {
                newSize = Math.multiplyExact(size, count);
            } catch (ArithmeticException e) {
                throw new RaiseException(getContext(), coreExceptions().rangeError("new array size too large", this));
            }
            final Object store = Layouts.ARRAY.getStore(array);
            final Object newStore = newStoreNode.execute(newSize);
            for (int n = 0; n < count; n++) {
                copyToNode.execute(store, newStore, 0, n * size, size);
            }

            final DynamicObject result = allocateObjectNode
                    .allocate(Layouts.BASIC_OBJECT.getLogicalClass(array), newStore, newSize);
            propagateTaintNode.propagate(array, result);
            return result;
        }

        @Specialization(guards = "count < 0")
        protected DynamicObject mulNeg(DynamicObject array, long count) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative argument", this));
        }

        @Specialization(guards = { "!isEmptyArray(array)", "count >= 0", "!fitsInInteger(count)" })
        protected DynamicObject mulLong(DynamicObject array, long count) {
            throw new RaiseException(getContext(), coreExceptions().rangeError("array size too big", this));
        }

        @Specialization(guards = { "isEmptyArray(array)", "strategy.matches(array)" })
        protected DynamicObject mulEmpty(DynamicObject array, long count,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode) {
            final Object newStore = newStoreNode.execute(0);

            final DynamicObject result = allocateObjectNode
                    .allocate(Layouts.BASIC_OBJECT.getLogicalClass(array), newStore, 0);
            propagateTaintNode.propagate(array, result);
            return result;
        }

        @Specialization(guards = { "!isInteger(count)", "!isLong(count)" })
        protected Object fallback(DynamicObject array, Object count) {
            return FAILURE;
        }
    }

    @CoreMethod(
            names = { "[]", "slice" },
            required = 1,
            optional = 1,
            lowerFixnum = { 1, 2 },
            argumentNames = { "index_start_or_range", "length" })
    public abstract static class IndexNode extends ArrayIndexNode {

        @Child private CallDispatchHeadNode fallbackNode;

        @Override
        protected Object fallback(DynamicObject array, Object start, Object length) {
            if (fallbackNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fallbackNode = insert(CallDispatchHeadNode.createPrivate());
            }

            return fallbackNode.call(array, "element_reference_fallback", start, length);
        }
    }

    @Primitive(
            name = "array_aref",
            lowerFixnum = { 1, 2 },
            argumentNames = { "index_start_or_range", "length" })
    public abstract static class IndexPrimitiveNode extends ArrayIndexNode {

        protected abstract RubyNode[] getArguments();

        @Override
        protected Object fallback(DynamicObject array, Object start, Object length) {
            throw new UnsupportedSpecializationException(this, getArguments(), array, start, length);
        }
    }

    @CoreMethod(
            names = "[]=",
            required = 2,
            optional = 1,
            lowerFixnum = { 1, 2 },
            raiseIfFrozenSelf = true,
            argumentNames = { "index_start_or_range", "length_or_value", "value" })
    public abstract static class IndexSetNode extends ArrayIndexSetNode {

        @Child private CallDispatchHeadNode fallbackNode;

        @Override
        protected Object fallback(DynamicObject array, Object index, Object length, Object value) {
            if (fallbackNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fallbackNode = insert(CallDispatchHeadNode.createPrivate());
            }

            return fallbackNode.call(array, "element_set_fallback", index, length, value);
        }
    }

    @Primitive(
            name = "array_aset",
            raiseIfFrozen = 0,
            lowerFixnum = { 1, 2 },
            argumentNames = { "index_start_or_range", "length_or_value", "value" })
    public abstract static class IndexSetPrimitiveNode extends ArrayIndexSetNode {

        protected abstract RubyNode[] getArguments();

        @Override
        protected Object fallback(DynamicObject array, Object index, Object length, Object value) {
            throw new UnsupportedSpecializationException(this, getArguments(), array, index, length, value);
        }
    }

    @CoreMethod(names = "at", required = 1, lowerFixnum = 1)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "index", type = RubyNode.class)
    public abstract static class AtNode extends CoreMethodNode {

        @Child private ArrayReadDenormalizedNode readNode;

        @CreateCast("index")
        protected RubyNode coerceOtherToInt(RubyNode index) {
            return FixnumLowerNodeGen.create(ToIntNodeGen.create(index));
        }

        @Specialization
        protected Object at(DynamicObject array, int index) {
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(ArrayReadDenormalizedNodeGen.create(null, null));
            }
            return readNode.executeRead(array, index);
        }

    }

    @CoreMethod(names = "clear", raiseIfFrozenSelf = true)
    @ReportPolymorphism
    public abstract static class ClearNode extends ArrayCoreMethodNode {

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected DynamicObject clear(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            strategy.setStoreAndSize(array, ArrayStrategy.NULL_ARRAY_STORE, 0);
            return array;
        }

    }

    @CoreMethod(names = "compact")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class CompactNode extends ArrayCoreMethodNode {

        @Specialization(guards = { "strategy.matches(array)", "strategy.isPrimitive()" }, limit = "STORAGE_STRATEGIES")
        protected DynamicObject compactPrimitive(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode) {
            final int size = strategy.getSize(array);
            Object compactMirror = extractRangeCopyOnWriteNode.execute(array, 0, size);
            return createArray(compactMirror, size);
        }

        @Specialization(guards = { "strategy.matches(array)", "!strategy.isPrimitive()" }, limit = "STORAGE_STRATEGIES")
        protected Object compactObjectsNonMutable(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached ArrayBuilderNode arrayBuilder) {
            final int size = strategy.getSize(array);
            final Object store = Layouts.ARRAY.getStore(array);
            Object newStore = arrayBuilder.start(size);

            int m = 0;

            for (int n = 0; n < size; n++) {
                Object v = getNode.execute(store, n);
                if (v != nil()) {
                    newStore = arrayBuilder.appendValue(newStore, m, v);
                    m++;
                }
            }

            return createArray(arrayBuilder.finish(newStore, m), m);
        }

    }

    @CoreMethod(names = "compact!", raiseIfFrozenSelf = true)
    @ReportPolymorphism
    public abstract static class CompactBangNode extends ArrayCoreMethodNode {

        @Specialization(guards = { "strategy.matches(array)", "strategy.isPrimitive()" }, limit = "STORAGE_STRATEGIES")
        @ReportPolymorphism.Exclude
        protected DynamicObject compactNotObjects(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy) {
            return nil();
        }

        @Specialization(guards = { "strategy.matches(array)", "!strategy.isPrimitive()" }, limit = "STORAGE_STRATEGIES")
        protected Object compactObjectsNonMutable(DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("mutableStrategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNOde) {
            final int size = strategy.getSize(array);
            final Object oldStore = Layouts.ARRAY.getStore(array);
            final Object newStore;
            if (strategy != mutableStrategy) {
                newStore = newStoreNOde.execute(size);
            } else {
                newStore = oldStore;
            }

            int m = 0;

            for (int n = 0; n < size; n++) {
                Object v = getNode.execute(oldStore, n);
                if (v != nil()) {
                    setNode.execute(newStore, m, v);
                    m++;
                }
            }

            strategy.setStoreAndSize(array, newStore, m);

            if (m == size) {
                return nil();
            } else {
                return array;
            }
        }

    }

    @CoreMethod(names = "concat", optional = 1, rest = true, raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "first", type = RubyNode.class)
    @NodeChild(value = "rest", type = RubyNode.class)
    public abstract static class ConcatNode extends CoreMethodNode {

        @Specialization(guards = "rest.length == 0")
        protected DynamicObject concatZero(DynamicObject array, NotProvided first, Object[] rest) {
            return array;
        }

        @Specialization(guards = "rest.length == 0")
        protected DynamicObject concatOne(DynamicObject array, DynamicObject first, Object[] rest,
                @Cached("createInternal()") ToAryNode toAryNode,
                @Cached ArrayAppendManyNode appendManyNode) {
            appendManyNode.executeAppendMany(array, toAryNode.executeToAry(first));
            return array;
        }

        @ExplodeLoop
        @Specialization(guards = { "wasProvided(first)", "rest.length > 0", "rest.length == cachedLength" })
        protected Object concatMany(DynamicObject array, DynamicObject first, Object[] rest,
                @Cached("rest.length") int cachedLength,
                @Cached("createInternal()") ToAryNode toAryNode,
                @Cached ArrayAppendManyNode appendManyNode,
                @Cached("createBinaryProfile()") ConditionProfile selfArgProfile,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeNode) {
            int size = Layouts.ARRAY.getSize(array);
            Object store = extractRangeNode.execute(array, 0, size);
            DynamicObject copy = createArray(store, size);
            DynamicObject result = appendManyNode.executeAppendMany(array, toAryNode.executeToAry(first));
            for (int i = 0; i < cachedLength; ++i) {
                if (selfArgProfile.profile(rest[i] == array)) {
                    result = appendManyNode.executeAppendMany(array, copy);
                } else {
                    result = appendManyNode.executeAppendMany(array, toAryNode.executeToAry(rest[i]));
                }
            }
            return result;
        }

        /** Same implementation as {@link #concatMany}, safe for the use of {@code cachedLength} */
        @Specialization(guards = { "wasProvided(first)", "rest.length > 0" }, replaces = "concatMany")
        protected Object concatManyGeneral(DynamicObject array, DynamicObject first, Object[] rest,
                @Cached("createInternal()") ToAryNode toAryNode,
                @Cached ArrayAppendManyNode appendManyNode,
                @Cached("createBinaryProfile()") ConditionProfile selfArgProfile,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeNode) {
            int size = Layouts.ARRAY.getSize(array);
            Object store = extractRangeNode.execute(array, 0, size);

            DynamicObject result = appendManyNode.executeAppendMany(array, toAryNode.executeToAry(first));
            for (Object arg : rest) {
                if (selfArgProfile.profile(arg == array)) {
                    result = appendManyNode.executeAppendMany(array, createArray(store, size));
                } else {
                    result = appendManyNode.executeAppendMany(array, toAryNode.executeToAry(arg));
                }
            }
            return result;
        }
    }

    @CoreMethod(names = "delete", required = 1, needsBlock = true)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class DeleteNode extends YieldingCoreMethodNode {

        @Child private SameOrEqualNode sameOrEqualNode = SameOrEqualNode.create();
        @Child private TypeNodes.CheckFrozenNode raiseIfFrozenNode;

        @Specialization(
                guards = { "strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object delete(VirtualFrame frame, DynamicObject array, Object value, Object maybeBlock,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("strategy.setNode()") ArrayOperationNodes.ArraySetNode setNode) {
            final Object store = Layouts.ARRAY.getStore(array);

            Object found = nil();

            int i = 0;
            int n = 0;
            while (n < strategy.getSize(array)) {
                final Object stored = getNode.execute(store, n);

                if (sameOrEqualNode.executeSameOrEqual(frame, stored, value)) {
                    checkFrozen(array);
                    found = stored;
                    n++;
                } else {
                    if (i != n) {
                        setNode.execute(store, i, getNode.execute(store, n));
                    }

                    i++;
                    n++;
                }
            }

            if (i != n) {
                strategy.setStoreAndSize(array, store, i);
                return found;
            } else {
                if (maybeBlock == NotProvided.INSTANCE) {
                    return nil();
                } else {
                    return yield((DynamicObject) maybeBlock, value);
                }
            }
        }

        @Specialization(
                guards = { "!strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object delete(VirtualFrame frame, DynamicObject array, Object value, Object maybeBlock,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("mutableStrategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode) {
            final int size = strategy.getSize(array);
            final Object oldStore = Layouts.ARRAY.getStore(array);
            final Object newStore = newStoreNode.execute(size);

            Object found = nil();

            int i = 0;
            int n = 0;
            while (n < size) {
                final Object stored = getNode.execute(oldStore, n);

                if (sameOrEqualNode.executeSameOrEqual(frame, stored, value)) {
                    checkFrozen(array);
                    found = stored;
                    n++;
                } else {
                    setNode.execute(newStore, i, getNode.execute(oldStore, n));

                    i++;
                    n++;
                }
            }

            if (i != n) {
                strategy.setStoreAndSize(array, newStore, i);
                return found;
            } else {
                if (maybeBlock == NotProvided.INSTANCE) {
                    return nil();
                } else {
                    return yield((DynamicObject) maybeBlock, value);
                }
            }
        }

        public void checkFrozen(Object object) {
            if (raiseIfFrozenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseIfFrozenNode = insert(TypeNodes.CheckFrozenNode.create());
            }
            raiseIfFrozenNode.execute(object);
        }

    }

    @CoreMethod(names = "delete_at", required = 1, raiseIfFrozenSelf = true, lowerFixnum = 1)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "index", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class DeleteAtNode extends CoreMethodNode {

        @CreateCast("index")
        protected RubyNode coerceOtherToInt(RubyNode index) {
            return ToIntNodeGen.create(index);
        }

        @Specialization(
                guards = { "strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object deleteAt(DynamicObject array, int index,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                @Cached("createBinaryProfile()") ConditionProfile notInBoundsProfile) {
            final int size = strategy.getSize(array);
            final int i = ArrayOperations.normalizeIndex(size, index, negativeIndexProfile);

            if (notInBoundsProfile.profile(i < 0 || i >= size)) {
                return nil();
            } else {
                final Object store = Layouts.ARRAY.getStore(array);
                final Object value = getNode.execute(store, i);
                copyToNode.execute(store, store, i + 1, i, size - i - 1);
                strategy.setStoreAndSize(array, store, size - 1);
                return value;
            }
        }

        @Specialization(
                guards = { "!strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "ARRAY_STRATEGIES")
        protected Object deleteAtCopying(DynamicObject array, int index,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("createBinaryProfile()") ConditionProfile negativeIndexProfile,
                @Cached("createBinaryProfile()") ConditionProfile notInBoundsProfile) {
            final int size = strategy.getSize(array);
            final int i = ArrayOperations.normalizeIndex(size, index, negativeIndexProfile);

            if (notInBoundsProfile.profile(i < 0 || i >= size)) {
                return nil();
            } else {
                final Object store = Layouts.ARRAY.getStore(array);
                final Object mutableStore = newStoreNode.execute(size);
                copyToNode.execute(store, mutableStore, 0, 0, i);
                final Object value = getNode.execute(store, i);
                copyToNode.execute(store, mutableStore, i + 1, i, size - i - 1);
                strategy.setStoreAndSize(array, mutableStore, size - 1);
                return value;
            }
        }

    }

    @CoreMethod(names = "each", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class EachNode extends YieldingCoreMethodNode implements ArrayElementConsumerNode {

        @Specialization
        protected Object each(DynamicObject array, DynamicObject block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(array, block, 0, this);
        }

        @Override
        public void accept(DynamicObject array, DynamicObject block, Object element, int index) {
            yield(block, element);
        }

    }

    @Primitive(name = "array_each_with_index")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class EachWithIndexNode extends PrimitiveArrayArgumentsNode
            implements ArrayElementConsumerNode {

        @Child private YieldNode dispatchNode = YieldNode.create();

        @Specialization
        protected Object eachOther(DynamicObject array, DynamicObject block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(array, block, 0, this);
        }

        @Override
        public void accept(DynamicObject array, DynamicObject block, Object element, int index) {
            dispatchNode.executeDispatch(block, element, index);
        }

    }

    @Primitive(name = "array_equal")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EqualNode extends PrimitiveArrayArgumentsNode {

        @Child private SameOrEqualNode sameOrEqualNode = SameOrEqualNode.create();

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "strategy.matches(b)", "strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected boolean equalSamePrimitiveType(VirtualFrame frame, DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("createBinaryProfile()") ConditionProfile sameProfile,
                @Cached("createIdentityProfile()") IntValueProfile sizeProfile,
                @Cached("createBinaryProfile()") ConditionProfile sameSizeProfile,
                @Cached BranchProfile trueProfile,
                @Cached BranchProfile falseProfile) {

            if (sameProfile.profile(a == b)) {
                return true;
            }

            final int aSize = sizeProfile.profile(strategy.getSize(a));
            final int bSize = strategy.getSize(b);

            if (!sameSizeProfile.profile(aSize == bSize)) {
                return false;
            }

            final Object aStore = Layouts.ARRAY.getStore(a);
            final Object bStore = Layouts.ARRAY.getStore(b);

            for (int i = 0; i < aSize; i++) {
                if (!sameOrEqualNode
                        .executeSameOrEqual(frame, getNode.execute(aStore, i), getNode.execute(bStore, i))) {
                    falseProfile.enter();
                    return false;
                }
            }

            trueProfile.enter();
            return true;
        }

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "!strategy.matches(b)", "strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected Object equalDifferentPrimitiveType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy) {
            return FAILURE;
        }

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "!strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected Object equalNotPrimitiveType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyArray(b)")
        protected Object equalNotArray(DynamicObject a, Object b) {
            return FAILURE;
        }

    }

    @Primitive(name = "array_eql")
    @ImportStatic(ArrayGuards.class)
    public abstract static class EqlNode extends PrimitiveArrayArgumentsNode {

        @Child private SameOrEqlNode eqlNode = SameOrEqlNodeFactory.create(null);

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "strategy.matches(b)", "strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected boolean eqlSamePrimitiveType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("createBinaryProfile()") ConditionProfile sameProfile,
                @Cached("createIdentityProfile()") IntValueProfile sizeProfile,
                @Cached("createBinaryProfile()") ConditionProfile sameSizeProfile,
                @Cached BranchProfile trueProfile,
                @Cached BranchProfile falseProfile) {

            if (sameProfile.profile(a == b)) {
                return true;
            }

            final int aSize = sizeProfile.profile(strategy.getSize(a));
            final int bSize = strategy.getSize(b);

            if (!sameSizeProfile.profile(aSize == bSize)) {
                return false;
            }

            final Object aStore = Layouts.ARRAY.getStore(a);
            final Object bStore = Layouts.ARRAY.getStore(b);

            for (int i = 0; i < aSize; i++) {
                if (!eqlNode.executeSameOrEql(getNode.execute(aStore, i), getNode.execute(bStore, i))) {
                    falseProfile.enter();
                    return false;
                }
            }

            trueProfile.enter();
            return true;
        }

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "!strategy.matches(b)", "strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected Object eqlDifferentPrimitiveType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy) {
            return FAILURE;
        }

        @Specialization(
                guards = { "isRubyArray(b)", "strategy.matches(a)", "!strategy.isPrimitive()" },
                limit = "STORAGE_STRATEGIES")
        protected Object eqlNotPrimitiveType(DynamicObject a, DynamicObject b,
                @Cached("of(a)") ArrayStrategy strategy) {
            return FAILURE;
        }

        @Specialization(guards = "!isRubyArray(b)")
        protected Object eqlNotArray(DynamicObject a, Object b) {
            return FAILURE;
        }

    }

    @CoreMethod(names = "fill", rest = true, needsBlock = true, raiseIfFrozenSelf = true)
    @ReportPolymorphism
    public abstract static class FillNode extends ArrayCoreMethodNode {

        @Specialization(
                guards = { "args.length == 1", "strategy.matches(array)", "strategy.accepts(value(args))" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject fill(DynamicObject array, Object[] args, NotProvided block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached PropagateSharingNode propagateSharingNode) {
            final Object value = args[0];
            propagateSharingNode.propagate(array, value);

            final Object store = Layouts.ARRAY.getStore(array);
            final int size = strategy.getSize(array);
            for (int i = 0; i < size; i++) {
                setNode.execute(store, i, value);
            }
            return array;
        }

        protected Object value(Object[] args) {
            return args[0];
        }

        @Specialization
        protected Object fillFallback(VirtualFrame frame, DynamicObject array, Object[] args, NotProvided block,
                @Cached("createPrivate()") CallDispatchHeadNode callFillInternal) {
            return callFillInternal.call(array, "fill_internal", args);
        }

        @Specialization
        protected Object fillFallback(VirtualFrame frame, DynamicObject array, Object[] args, DynamicObject block,
                @Cached("createPrivate()") CallDispatchHeadNode callFillInternal) {
            return callFillInternal.callWithBlock(array, "fill_internal", block, args);
        }

    }

    @CoreMethod(names = "hash_internal", visibility = Visibility.PRIVATE)
    @ReportPolymorphism
    public abstract static class HashNode extends ArrayCoreMethodNode {

        private static final int CLASS_SALT = 42753062; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

        @Child private ToIntNode toIntNode;

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected long hash(VirtualFrame frame, DynamicObject array,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("createPrivate()") CallDispatchHeadNode toHashNode) {
            final int size = strategy.getSize(array);
            long h = getContext().getHashing(this).start(size);
            h = Hashing.update(h, CLASS_SALT);
            final Object store = Layouts.ARRAY.getStore(array);

            for (int n = 0; n < size; n++) {
                final Object value = getNode.execute(store, n);
                final long valueHash = toLong(toHashNode.call(value, "hash"));
                h = Hashing.update(h, valueHash);
            }

            return Hashing.end(h);
        }

        private long toLong(Object indexObject) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }
            final Object result = toIntNode.executeIntOrLong(indexObject);
            if (result instanceof Integer) {
                return (int) result;
            } else {
                return (long) result;
            }
        }

    }

    @CoreMethod(names = "include?", required = 1)
    @ReportPolymorphism
    public abstract static class IncludeNode extends ArrayCoreMethodNode {

        @Child private SameOrEqualNode sameOrEqualNode = SameOrEqualNode.create();

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected boolean include(VirtualFrame frame, DynamicObject array, Object value,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final Object store = Layouts.ARRAY.getStore(array);

            for (int n = 0; n < strategy.getSize(array); n++) {
                final Object stored = getNode.execute(store, n);

                if (sameOrEqualNode.executeSameOrEqual(frame, stored, value)) {
                    return true;
                }
            }

            return false;
        }

    }

    @CoreMethod(
            names = "initialize",
            needsBlock = true,
            optional = 2,
            raiseIfFrozenSelf = true,
            lowerFixnum = 1,
            argumentNames = { "size_or_copy", "filling_value", "block" })
    @ImportStatic(ArrayGuards.class)
    public abstract static class InitializeNode extends YieldingCoreMethodNode {

        @Child private ToIntNode toIntNode;
        @Child private CallDispatchHeadNode toAryNode;
        @Child private KernelNodes.RespondToNode respondToToAryNode;

        public abstract DynamicObject executeInitialize(VirtualFrame frame, DynamicObject array, Object size,
                Object fillingValue, Object block);

        @Specialization
        protected DynamicObject initializeNoArgs(DynamicObject array, NotProvided size, NotProvided fillingValue,
                NotProvided block) {
            setStoreAndSize(array, ArrayStrategy.NULL_ARRAY_STORE, 0);
            return array;
        }

        @Specialization
        protected DynamicObject initializeOnlyBlock(DynamicObject array, NotProvided size, NotProvided fillingValue,
                DynamicObject block) {
            setStoreAndSize(array, ArrayStrategy.NULL_ARRAY_STORE, 0);
            return array;
        }

        @TruffleBoundary
        @Specialization(guards = "size < 0")
        protected DynamicObject initializeNegativeIntSize(
                DynamicObject array, int size, Object unusedFillingValue, Object unusedBlock) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative array size", this));
        }

        @TruffleBoundary
        @Specialization(guards = "size < 0")
        protected DynamicObject initializeNegativeLongSize(
                DynamicObject array, long size, Object unusedFillingValue, Object unusedBlock) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("negative array size", this));
        }

        protected static final long MAX_INT = Integer.MAX_VALUE;

        @TruffleBoundary
        @Specialization(guards = "size >= MAX_INT")
        protected DynamicObject initializeSizeTooBig(DynamicObject array, long size, NotProvided fillingValue,
                NotProvided block) {
            throw new RaiseException(getContext(), coreExceptions().argumentError("array size too big", this));
        }

        @Specialization(guards = "size >= 0")
        protected DynamicObject initializeWithSizeNoValue(DynamicObject array, int size, NotProvided fillingValue,
                NotProvided block) {
            final Object[] store = new Object[size];
            Arrays.fill(store, nil());
            setStoreAndSize(array, store, size);
            return array;
        }

        @Specialization(
                guards = { "size >= 0", "wasProvided(fillingValue)", "strategy.specializesFor(fillingValue)" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject initializeWithSizeAndValue(DynamicObject array, int size, Object fillingValue,
                NotProvided block,
                @Cached("forValue(fillingValue)") ArrayStrategy strategy,
                @Cached("strategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("strategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("createBinaryProfile()") ConditionProfile needsFill,
                @Cached PropagateSharingNode propagateSharingNode) {
            final Object store = newStoreNode.execute(size);
            if (needsFill.profile(!strategy.isDefaultValue(fillingValue))) {
                propagateSharingNode.propagate(array, fillingValue);
                for (int i = 0; i < size; i++) {
                    setNode.execute(store, i, fillingValue);
                }
            }
            setStoreAndSize(array, store, size);
            return array;
        }

        @Specialization(
                guards = { "wasProvided(size)", "!isInteger(size)", "!isLong(size)", "wasProvided(fillingValue)" })
        protected DynamicObject initializeSizeOther(VirtualFrame frame, DynamicObject array, Object size,
                Object fillingValue, NotProvided block) {
            int intSize = toInt(size);
            return executeInitialize(frame, array, intSize, fillingValue, block);
        }

        // With block

        @Specialization(guards = "size >= 0")
        protected Object initializeBlock(DynamicObject array, int size, Object unusedFillingValue, DynamicObject block,
                @Cached ArrayBuilderNode arrayBuilder,
                @Cached PropagateSharingNode propagateSharingNode) {
            Object store = arrayBuilder.start(size);

            int n = 0;
            try {
                for (; n < size; n++) {
                    final Object value = yield(block, n);
                    propagateSharingNode.propagate(array, value);
                    store = arrayBuilder.appendValue(store, n, value);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
                setStoreAndSize(array, arrayBuilder.finish(store, n), n);
            }

            return array;
        }

        @Specialization(guards = "isRubyArray(copy)")
        protected DynamicObject initializeFromArray(DynamicObject array, DynamicObject copy, NotProvided unusedValue,
                Object maybeBlock,
                @Cached ReplaceNode replaceNode) {
            replaceNode.executeReplace(array, copy);
            return array;
        }

        @Specialization(
                guards = { "!isInteger(object)", "!isLong(object)", "wasProvided(object)", "!isRubyArray(object)" })
        protected DynamicObject initialize(VirtualFrame frame, DynamicObject array, Object object,
                NotProvided unusedValue, NotProvided block) {
            DynamicObject copy = null;
            if (respondToToAry(frame, object)) {
                Object toAryResult = callToAry(frame, object);
                if (RubyGuards.isRubyArray(toAryResult)) {
                    copy = (DynamicObject) toAryResult;
                }
            }

            if (copy != null) {
                return executeInitialize(frame, array, copy, NotProvided.INSTANCE, NotProvided.INSTANCE);
            } else {
                int size = toInt(object);
                return executeInitialize(frame, array, size, NotProvided.INSTANCE, NotProvided.INSTANCE);
            }
        }

        public boolean respondToToAry(VirtualFrame frame, Object object) {
            if (respondToToAryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                respondToToAryNode = insert(KernelNodesFactory.RespondToNodeFactory.create(null, null, null));
            }
            return respondToToAryNode.executeDoesRespondTo(frame, object, coreStrings().TO_ARY.createInstance(), true);
        }

        protected Object callToAry(VirtualFrame frame, Object object) {
            if (toAryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toAryNode = insert(CallDispatchHeadNode.createPrivate());
            }
            return toAryNode.call(object, "to_ary");
        }

        protected int toInt(Object value) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }
            return toIntNode.doInt(value);
        }

    }

    @CoreMethod(names = "initialize_copy", required = 1, raiseIfFrozenSelf = true)
    @NodeChild(value = "self", type = RubyNode.class)
    @NodeChild(value = "from", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    public abstract static class InitializeCopyNode extends CoreMethodNode {

        @CreateCast("from")
        protected RubyNode coerceOtherToAry(RubyNode other) {
            return ToAryNodeGen.create(other);
        }

        @Specialization
        protected DynamicObject initializeCopy(DynamicObject self, DynamicObject from,
                @Cached ReplaceNode replaceNode) {
            if (self == from) {
                return self;
            }
            replaceNode.executeReplace(self, from);
            return self;
        }

    }

    @Primitive(name = "array_inject")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class InjectNode extends YieldingCoreMethodNode {

        @Child private CallDispatchHeadNode dispatch = CallDispatchHeadNode.createPublic();

        // With block

        @Specialization(guards = { "isEmptyArray(array)", "wasProvided(initialOrSymbol)", "block != nil()" })
        @ReportPolymorphism.Exclude
        protected Object injectEmptyArray(DynamicObject array, Object initialOrSymbol, NotProvided symbol,
                DynamicObject block) {
            return initialOrSymbol;
        }

        @Specialization(guards = { "isEmptyArray(array)", "block != nil()" })
        @ReportPolymorphism.Exclude
        protected Object injectEmptyArrayNoInitial(DynamicObject array, NotProvided initialOrSymbol,
                NotProvided symbol,
                DynamicObject block) {
            return nil();
        }

        @Specialization(
                guards = {
                        "strategy.matches(array)",
                        "!isEmptyArray(array)",
                        "wasProvided(initialOrSymbol)",
                        "block != nil()" },
                limit = "STORAGE_STRATEGIES")
        protected Object injectWithInitial(DynamicObject array, Object initialOrSymbol, NotProvided symbol,
                DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final Object store = Layouts.ARRAY.getStore(array);
            return injectBlockHelper(getNode, array, block, store, initialOrSymbol, 0);
        }

        @Specialization(
                guards = { "strategy.matches(array)", "!isEmptyArray(array)", "block != nil()" },
                limit = "STORAGE_STRATEGIES")
        protected Object injectNoInitial(DynamicObject array, NotProvided initialOrSymbol, NotProvided symbol,
                DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final Object store = Layouts.ARRAY.getStore(array);
            return injectBlockHelper(getNode, array, block, store, getNode.execute(store, 0), 1);
        }

        public Object injectBlockHelper(ArrayOperationNodes.ArrayGetNode getNode, DynamicObject array,
                DynamicObject block, Object store, Object initial, int start) {
            Object accumulator = initial;
            int n = start;
            try {
                for (; n < getSize(array); n++) {
                    accumulator = yield(block, accumulator, getNode.execute(store, n));
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }

            return accumulator;
        }

        // With Symbol

        @Specialization(
                guards = {
                        "isRubySymbol(symbol)",
                        "isEmptyArray(array)",
                        "wasProvided(initialOrSymbol)",
                        "block == nil()" })
        protected Object injectSymbolEmptyArray(DynamicObject array, Object initialOrSymbol, DynamicObject symbol,
                DynamicObject block) {
            return initialOrSymbol;
        }

        @Specialization(guards = { "isRubySymbol(initialOrSymbol)", "isEmptyArray(array)", "block == nil()" })
        protected Object injectSymbolEmptyArrayNoInitial(DynamicObject array, DynamicObject initialOrSymbol,
                NotProvided symbol,
                DynamicObject block) {
            return nil();
        }

        @Specialization(
                guards = {
                        "isRubySymbol(symbol)",
                        "strategy.matches(array)",
                        "!isEmptyArray(array)",
                        "wasProvided(initialOrSymbol)",
                        "block == nil()" },
                limit = "STORAGE_STRATEGIES")
        protected Object injectSymbolWithInitial(VirtualFrame frame, DynamicObject array, Object initialOrSymbol,
                DynamicObject symbol, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final Object store = Layouts.ARRAY.getStore(array);
            return injectSymbolHelper(frame, array, symbol, getNode, store, initialOrSymbol, 0);
        }

        @Specialization(
                guards = {
                        "isRubySymbol(initialOrSymbol)",
                        "strategy.matches(array)",
                        "!isEmptyArray(array)",
                        "block == nil()" },
                limit = "STORAGE_STRATEGIES")
        protected Object injectSymbolNoInitial(VirtualFrame frame, DynamicObject array, DynamicObject initialOrSymbol,
                NotProvided symbol, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final Object store = Layouts.ARRAY.getStore(array);
            return injectSymbolHelper(frame, array, initialOrSymbol, getNode, store, getNode.execute(store, 0), 1);
        }

        public Object injectSymbolHelper(VirtualFrame frame, DynamicObject array, DynamicObject symbol,
                ArrayOperationNodes.ArrayGetNode getNode, Object store, Object initial, int start) {
            Object accumulator = initial;
            int n = start;

            try {
                for (; n < getSize(array); n++) {
                    accumulator = dispatch
                            .dispatch(frame, accumulator, symbol, null, new Object[]{ getNode.execute(store, n) });
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }
            return accumulator;
        }

    }

    @CoreMethod(names = { "map", "collect" }, needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class MapNode extends YieldingCoreMethodNode {

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected Object map(DynamicObject array, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached ArrayBuilderNode arrayBuilder) {
            final Object store = Layouts.ARRAY.getStore(array);
            final int size = strategy.getSize(array);
            Object mappedStore = arrayBuilder.start(size);

            int n = 0;
            try {
                for (; n < strategy.getSize(array); n++) {
                    final Object mappedValue = yield(block, getNode.execute(store, n));
                    mappedStore = arrayBuilder.appendValue(mappedStore, n, mappedValue);
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }

            return createArray(arrayBuilder.finish(mappedStore, size), size);
        }

    }

    @CoreMethod(names = { "map!", "collect!" }, needsBlock = true, enumeratorSize = "size", raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    public abstract static class MapInPlaceNode extends YieldingCoreMethodNode implements ArrayElementConsumerNode {

        @Child private ArrayWriteNormalizedNode writeNode = ArrayWriteNormalizedNodeGen.create();

        @Specialization
        protected Object map(DynamicObject array, DynamicObject block,
                @Cached ArrayEachIteratorNode iteratorNode) {
            return iteratorNode.execute(array, block, 0, this);
        }

        @Override
        public void accept(DynamicObject array, DynamicObject block, Object element, int index) {
            writeNode.executeWrite(array, index, yield(block, element));
        }

    }

    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "format", type = RubyNode.class)
    @CoreMethod(names = "pack", required = 1, taintFrom = 1)
    @ImportStatic({ StringCachingGuards.class, StringOperations.class })
    @ReportPolymorphism
    public abstract static class PackNode extends CoreMethodNode {

        @Child private RopeNodes.MakeLeafRopeNode makeLeafRopeNode;
        @Child private StringNodes.MakeStringNode makeStringNode;
        @Child private TaintNode taintNode;
        @Child private WriteObjectFieldNode writeAssociatedNode;

        private final BranchProfile exceptionProfile = BranchProfile.create();
        private final ConditionProfile resizeProfile = ConditionProfile.createBinaryProfile();

        @CreateCast("format")
        protected RubyNode coerceFormat(RubyNode format) {
            return ToStrNodeGen.create(format);
        }

        @Specialization(guards = "equalNode.execute(rope(format), cachedFormat)", limit = "getCacheLimit()")
        protected DynamicObject packCached(
                DynamicObject array,
                DynamicObject format,
                @Cached("privatizeRope(format)") Rope cachedFormat,
                @Cached("ropeLength(cachedFormat)") int cachedFormatLength,
                @Cached("create(compileFormat(format))") DirectCallNode callPackNode,
                @Cached RopeNodes.EqualNode equalNode) {
            final BytesResult result;

            try {
                result = (BytesResult) callPackNode.call(
                        new Object[]{ getStore(array), getSize(array), false, null });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(this, e);
            }

            return finishPack(cachedFormatLength, result);
        }

        @Specialization(replaces = "packCached")
        protected DynamicObject packUncached(
                DynamicObject array,
                DynamicObject format,
                @Cached IndirectCallNode callPackNode) {
            final BytesResult result;

            try {
                result = (BytesResult) callPackNode.call(
                        compileFormat(format),
                        new Object[]{ getStore(array), getSize(array), false, null });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(this, e);
            }

            return finishPack(Layouts.STRING.getRope(format).byteLength(), result);
        }

        private DynamicObject finishPack(int formatLength, BytesResult result) {
            byte[] bytes = result.getOutput();

            if (resizeProfile.profile(bytes.length != result.getOutputLength())) {
                bytes = Arrays.copyOf(bytes, result.getOutputLength());
            }

            if (makeLeafRopeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                makeLeafRopeNode = insert(RopeNodes.MakeLeafRopeNode.create());
            }

            if (makeStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                makeStringNode = insert(StringNodes.MakeStringNode.create());
            }

            final DynamicObject string = makeStringNode.fromRope(makeLeafRopeNode.executeMake(
                    bytes,
                    result.getEncoding().getEncodingForLength(formatLength),
                    result.getStringCodeRange(),
                    result.getStringLength()));

            if (result.isTainted()) {
                if (taintNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    taintNode = insert(TaintNode.create());
                }

                taintNode.executeTaint(string);
            }

            if (result.getAssociated() != null) {
                if (writeAssociatedNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    writeAssociatedNode = insert(WriteObjectFieldNode.create());
                }

                writeAssociatedNode.write(string, Layouts.ASSOCIATED_IDENTIFIER, result.getAssociated());
            }

            return string;
        }

        @TruffleBoundary
        protected RootCallTarget compileFormat(DynamicObject format) {
            return new PackCompiler(getContext(), this).compile(format.toString());
        }

        protected int getCacheLimit() {
            return getContext().getOptions().PACK_CACHE;
        }

    }

    @CoreMethod(names = "pop", raiseIfFrozenSelf = true, optional = 1, lowerFixnum = 1)
    @ReportPolymorphism
    public abstract static class PopNode extends ArrayCoreMethodNode {

        @Child private ToIntNode toIntNode;
        @Child private ArrayPopOneNode popOneNode;

        public abstract Object executePop(DynamicObject array, Object n);

        @Specialization
        @ReportPolymorphism.Exclude
        protected Object pop(DynamicObject array, NotProvided n) {
            if (popOneNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                popOneNode = insert(ArrayPopOneNodeGen.create());
            }

            return popOneNode.executePopOne(array);
        }

        @Specialization(guards = "n < 0")
        @ReportPolymorphism.Exclude
        protected Object popNNegative(DynamicObject array, int n) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorNegativeArraySize(this));
        }

        @Specialization(guards = { "n >= 0", "isEmptyArray(array)" })
        @ReportPolymorphism.Exclude
        protected Object popEmpty(DynamicObject array, int n) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(guards = { "n == 0", "!isEmptyArray(array)" })
        @ReportPolymorphism.Exclude
        protected Object popZeroNotEmpty(DynamicObject array, int n) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(
                guards = { "n > 0", "!isEmptyArray(array)", "!strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object popNotEmptySharedStorage(DynamicObject array, int n,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeNode()") ArrayOperationNodes.ArrayExtractRangeNode extractRangeNode,
                @Cached("createBinaryProfile()") ConditionProfile minProfile) {
            final int size = strategy.getSize(array);
            final int numPop = minProfile.profile(size < n) ? size : n;
            final Object store = Layouts.ARRAY.getStore(array);

            // Extract values in a new array
            final Object popped = extractRangeNode.execute(store, size - numPop, size);

            // Remove the end from the original array.
            setStoreAndSize(array, extractRangeNode.execute(store, 0, size - numPop), size - numPop);

            return createArray(popped, numPop);
        }

        @Specialization(
                guards = { "n > 0", "!isEmptyArray(array)", "strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object popNotEmptyUnsharedStorage(DynamicObject array, int n,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("createBinaryProfile()") ConditionProfile minProfile) {
            final int size = strategy.getSize(array);
            final int numPop = minProfile.profile(size < n) ? size : n;
            final Object store = Layouts.ARRAY.getStore(array);

            // Extract values in a new array
            final Object popped = newStoreNode.execute(numPop);
            copyToNode.execute(store, popped, size - numPop, 0, numPop);

            // Remove the end from the original array.
            final Object filler = newStoreNode.execute(numPop);
            copyToNode.execute(filler, store, 0, size - numPop, numPop);
            setSize(array, size - numPop);

            return createArray(popped, numPop);
        }

        @Specialization(guards = { "wasProvided(n)", "!isInteger(n)", "!isLong(n)" })
        protected Object popNToInt(DynamicObject array, Object n) {
            return executePop(array, toInt(n));
        }

        private int toInt(Object indexObject) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }
            return toIntNode.doInt(indexObject);
        }

    }

    @CoreMethod(names = "<<", raiseIfFrozenSelf = true, required = 1)
    public abstract static class AppendNode extends ArrayCoreMethodNode {

        @Child private ArrayAppendOneNode appendOneNode = ArrayAppendOneNode.create();

        @Specialization
        protected DynamicObject append(DynamicObject array, Object value) {
            return appendOneNode.executeAppendOne(array, value);
        }

    }

    @CoreMethod(names = { "push", "append" }, rest = true, optional = 1, raiseIfFrozenSelf = true)
    public abstract static class PushNode extends ArrayCoreMethodNode {

        @Child private ArrayAppendOneNode appendOneNode = ArrayAppendOneNode.create();

        @Specialization(guards = "rest.length == 0")
        protected DynamicObject pushZero(DynamicObject array, NotProvided value, Object[] rest) {
            return array;
        }

        @Specialization(guards = { "rest.length == 0", "wasProvided(value)" })
        protected DynamicObject pushOne(DynamicObject array, Object value, Object[] rest) {
            return appendOneNode.executeAppendOne(array, value);
        }

        @Specialization(guards = { "rest.length > 0", "wasProvided(value)" })
        protected DynamicObject pushMany(VirtualFrame frame, DynamicObject array, Object value, Object[] rest) {
            // NOTE (eregon): Appending one by one here to avoid useless generalization to Object[]
            // if the arguments all fit in the current storage
            appendOneNode.executeAppendOne(array, value);
            for (int i = 0; i < rest.length; i++) {
                appendOneNode.executeAppendOne(array, rest[i]);
            }
            return array;
        }

    }

    @CoreMethod(names = "reject", needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class RejectNode extends YieldingCoreMethodNode {

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected Object rejectOther(DynamicObject array, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached ArrayBuilderNode arrayBuilder) {
            final Object store = Layouts.ARRAY.getStore(array);

            Object selectedStore = arrayBuilder.start(strategy.getSize(array));
            int selectedSize = 0;

            int n = 0;
            try {
                for (; n < strategy.getSize(array); n++) {
                    final Object value = getNode.execute(store, n);

                    if (!yieldIsTruthy(block, value)) {
                        selectedStore = arrayBuilder.appendValue(selectedStore, selectedSize, value);
                        selectedSize++;
                    }
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }

            return createArray(arrayBuilder.finish(selectedStore, selectedSize), selectedSize);
        }

    }

    @CoreMethod(names = "reject!", needsBlock = true, enumeratorSize = "size", raiseIfFrozenSelf = true)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class RejectInPlaceNode extends YieldingCoreMethodNode {

        @Specialization(guards = { "strategy.matches(array)" }, limit = "STORAGE_STRATEGIES")
        protected Object rejectInPlace(DynamicObject array, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("mutableStrategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("mutableStrategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("mutableStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("strategy.unshareNode()") ArrayOperationNodes.ArrayUnshareStorageNode unshareNode) {
            final Object mutableStore = unshareNode.execute(array);
            return rejectInPlaceInternal(array, block, getNode, setNode, newStoreNode, copyToNode, mutableStore);
        }

        private Object rejectInPlaceInternal(DynamicObject array, DynamicObject block,
                ArrayOperationNodes.ArrayGetNode getNode, ArrayOperationNodes.ArraySetNode setNode,
                ArrayOperationNodes.ArrayNewStoreNode newStoreNode, ArrayOperationNodes.ArrayCopyToNode copyToNode,
                Object store) {
            int i = 0;
            int n = 0;
            try {
                for (; n < Layouts.ARRAY.getSize(array); n++) {
                    final Object value = getNode.execute(store, n);
                    if (yieldIsTruthy(block, value)) {
                        continue;
                    }

                    if (i != n) {
                        setNode.execute(store, i, getNode.execute(store, n));
                    }

                    i++;
                }
            } finally {
                // Ensure we've iterated to the end of the array.
                for (; n < Layouts.ARRAY.getSize(array); n++) {
                    if (i != n) {
                        setNode.execute(store, i, getNode.execute(store, n));
                    }
                    i++;
                }

                // Null out the elements behind the size
                final Object filler = newStoreNode.execute(n - i);
                copyToNode.execute(filler, store, 0, i, n - i);
                setSize(array, i);

                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }

            if (i != n) {
                return array;
            } else {
                return nil();
            }
        }

    }

    @CoreMethod(names = "replace", required = 1, raiseIfFrozenSelf = true)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "other", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class ReplaceNode extends CoreMethodNode {

        public static ReplaceNode create() {
            return ReplaceNodeFactory.create(null, null);
        }

        @Child private PropagateSharingNode propagateSharingNode = PropagateSharingNode.create();

        public abstract DynamicObject executeReplace(DynamicObject array, DynamicObject other);

        @CreateCast("other")
        protected RubyNode coerceOtherToAry(RubyNode index) {
            return ToAryNodeGen.create(index);
        }

        @Specialization(
                guards = { "arrayStrategy.matches(array)", "otherStrategy.matches(other)" },
                limit = "ARRAY_STRATEGIES")
        protected DynamicObject replace(DynamicObject array, DynamicObject other,
                @Cached("of(array)") ArrayStrategy arrayStrategy,
                @Cached("of(other)") ArrayStrategy otherStrategy,
                @Cached("otherStrategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode) {
            propagateSharingNode.propagate(array, other);

            final int size = getSize(other);
            final Object copy = extractRangeCopyOnWriteNode.execute(other, 0, size);
            arrayStrategy.setStoreAndSize(array, copy, size);
            return array;
        }

    }

    @Primitive(name = "array_rotate", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class RotateNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = { "strategy.matches(array)" }, limit = "STORAGE_STRATEGIES")
        protected DynamicObject rotate(DynamicObject array, int rotation,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("createIdentityProfile()") IntValueProfile sizeProfile,
                @Cached("createIdentityProfile()") IntValueProfile rotationProfile) {
            final int size = sizeProfile.profile(strategy.getSize(array));
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;

            final Object original = Layouts.ARRAY.getStore(array);
            final Object rotated = newStoreNode.execute(size);
            rotateArrayCopy(rotation, size, copyToNode, original, rotated);
            return createArray(rotated, size);
        }

    }

    protected static void rotateArrayCopy(int rotation, int size, ArrayOperationNodes.ArrayCopyToNode copyToNode,
            Object original, Object rotated) {
        copyToNode.execute(original, rotated, rotation, 0, size - rotation);
        copyToNode.execute(original, rotated, 0, size - rotation, rotation);
    }

    @Primitive(name = "array_rotate_inplace", lowerFixnum = 1)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class RotateInplaceNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject rotate(DynamicObject array, int rotation,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("strategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("createIdentityProfile()") IntValueProfile sizeProfile,
                @Cached("createIdentityProfile()") IntValueProfile rotationProfile) {
            final int size = sizeProfile.profile(strategy.getSize(array));
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;
            final Object store = Layouts.ARRAY.getStore(array);

            if (CompilerDirectives.isPartialEvaluationConstant(size) &&
                    CompilerDirectives.isPartialEvaluationConstant(rotation) &&
                    size <= ArrayGuards.ARRAY_MAX_EXPLODE_SIZE) {
                rotateSmallExplode(getNode, setNode, rotation, size, store);
            } else {
                rotateReverse(getNode, setNode, rotation, size, store);
            }

            return array;
        }

        @Specialization(
                guards = { "!strategy.isStorageMutable()", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject rotateStorageNotMutable(DynamicObject array, int rotation,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("createIdentityProfile()") IntValueProfile sizeProfile,
                @Cached("createIdentityProfile()") IntValueProfile rotationProfile) {
            final int size = sizeProfile.profile(strategy.getSize(array));
            rotation = rotationProfile.profile(rotation);
            assert 0 < rotation && rotation < size;

            final Object original = Layouts.ARRAY.getStore(array);
            final Object rotated = newStoreNode.execute(size);
            rotateArrayCopy(rotation, size, copyToNode, original, rotated);
            setStoreAndSize(array, rotated, size);
            return array;
        }

        @ExplodeLoop
        protected void rotateSmallExplode(ArrayOperationNodes.ArrayGetNode getNode,
                ArrayOperationNodes.ArraySetNode setNode, int rotation, int size, Object store) {
            Object[] copy = new Object[size];
            for (int i = 0; i < size; i++) {
                copy[i] = getNode.execute(store, i);
            }
            for (int i = 0; i < size; i++) {
                int j = i + rotation;
                if (j >= size) {
                    j -= size;
                }
                setNode.execute(store, i, copy[j]);
            }
        }

        protected void rotateReverse(ArrayOperationNodes.ArrayGetNode getNode, ArrayOperationNodes.ArraySetNode setNode,
                int rotation, int size, Object store) {
            // Rotating by rotation in-place is equivalent to
            // replace([rotation..-1] + [0...rotation])
            // which is the same as reversing the whole array and
            // reversing each of the two parts so that elements are in the same order again.
            // This trick avoids constantly checking if indices are within array bounds
            // and accesses memory sequentially, even though it does perform 2*size reads and writes.
            // This is also what MRI and JRuby do.
            reverse(getNode, setNode, store, rotation, size);
            reverse(getNode, setNode, store, 0, rotation);
            reverse(getNode, setNode, store, 0, size);
        }

        private void reverse(ArrayOperationNodes.ArrayGetNode getNode, ArrayOperationNodes.ArraySetNode setNode,
                Object store, int from, int until) {
            int to = until - 1;
            while (from < to) {
                final Object tmp = getNode.execute(store, from);
                setNode.execute(store, from, getNode.execute(store, to));
                setNode.execute(store, to, tmp);
                from++;
                to--;
            }
        }

    }

    @CoreMethod(names = { "select", "filter" }, needsBlock = true, enumeratorSize = "size")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class SelectNode extends YieldingCoreMethodNode {

        @Specialization(guards = "strategy.matches(array)", limit = "STORAGE_STRATEGIES")
        protected Object selectOther(DynamicObject array, DynamicObject block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached ArrayBuilderNode arrayBuilder) {
            final Object store = Layouts.ARRAY.getStore(array);

            Object selectedStore = arrayBuilder.start(strategy.getSize(array));
            int selectedSize = 0;

            int n = 0;
            try {
                for (; n < strategy.getSize(array); n++) {
                    final Object value = getNode.execute(store, n);

                    if (yieldIsTruthy(block, value)) {
                        selectedStore = arrayBuilder.appendValue(selectedStore, selectedSize, value);
                        selectedSize++;
                    }
                }
            } finally {
                if (CompilerDirectives.inInterpreter()) {
                    LoopNode.reportLoopCount(this, n);
                }
            }

            return createArray(arrayBuilder.finish(selectedStore, selectedSize), selectedSize);
        }

    }

    @CoreMethod(names = "shift", raiseIfFrozenSelf = true, optional = 1, lowerFixnum = 1)
    @NodeChild(value = "array", type = RubyNode.class)
    @NodeChild(value = "n", type = RubyNode.class)
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class ShiftNode extends CoreMethodNode {

        @Child private ToIntNode toIntNode;

        public abstract Object executeShift(DynamicObject array, Object n);

        // No n, just shift 1 element and return it

        @Specialization(guards = "isEmptyArray(array)")
        @ReportPolymorphism.Exclude
        protected Object shiftEmpty(DynamicObject array, NotProvided n) {
            return nil();
        }

        @Specialization(guards = { "strategy.matches(array)", "!isEmptyArray(array)" }, limit = "STORAGE_STRATEGIES")
        protected Object shiftOther(DynamicObject array, NotProvided n,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode) {
            final int size = strategy.getSize(array);
            final Object value = getNode.execute(Layouts.ARRAY.getStore(array), 0);
            strategy.setStore(array, extractRangeCopyOnWriteNode.execute(array, 1, size));
            setSize(array, size - 1);

            return value;
        }

        // n given, shift the first n elements and return them as an Array

        @Specialization(guards = "n < 0")
        protected Object shiftNegative(DynamicObject array, int n) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorNegativeArraySize(this));
        }

        @Specialization(guards = "n == 0")
        protected Object shiftZero(DynamicObject array, int n) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(guards = { "n > 0", "isEmptyArray(array)" })
        protected Object shiftManyEmpty(DynamicObject array, int n) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @Specialization(
                guards = { "n > 0", "strategy.matches(array)", "!isEmptyArray(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object shiftMany(DynamicObject array, int n,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode1,
                @Cached("strategy.sharedStorageStrategy().extractRangeCopyOnWriteNode()") ArrayExtractRangeCopyOnWriteNode extractRangeCopyOnWriteNode2,
                @Cached("createBinaryProfile()") ConditionProfile minProfile) {
            final int size = strategy.getSize(array);
            final int numShift = minProfile.profile(size < n) ? size : n;
            // Extract values in a new array
            final Object result = extractRangeCopyOnWriteNode1.execute(array, 0, numShift);

            setStoreAndSize(array, extractRangeCopyOnWriteNode2.execute(array, numShift, size), size - numShift);

            return createArray(result, numShift);
        }

        @Specialization(guards = { "wasProvided(n)", "!isInteger(n)", "!isLong(n)" })
        protected Object shiftNToInt(DynamicObject array, Object n) {
            return executeShift(array, toInt(n));
        }

        private int toInt(Object indexObject) {
            if (toIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toIntNode = insert(ToIntNode.create());
            }
            return toIntNode.doInt(indexObject);
        }

    }

    @CoreMethod(names = { "size", "length" })
    public abstract static class SizeNode extends ArrayCoreMethodNode {

        @Specialization
        protected int size(DynamicObject array,
                @Cached("createIdentityProfile()") IntValueProfile profile) {
            return profile.profile(Layouts.ARRAY.getSize(array));
        }

    }

    @CoreMethod(names = "sort", needsBlock = true)
    @ReportPolymorphism
    public abstract static class SortNode extends ArrayCoreMethodNode {

        @Specialization(guards = "isEmptyArray(array)")
        @ReportPolymorphism.Exclude
        protected DynamicObject sortEmpty(DynamicObject array, Object unusedBlock) {
            return createArray(ArrayStrategy.NULL_ARRAY_STORE, 0);
        }

        @ExplodeLoop
        @Specialization(
                guards = { "!isEmptyArray(array)", "isSmall(array)", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected DynamicObject sortVeryShort(VirtualFrame frame, DynamicObject array, NotProvided block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.getNode()") ArrayOperationNodes.ArrayGetNode originalGetNode,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("mutableStrategy.getNode()") ArrayOperationNodes.ArrayGetNode getNode,
                @Cached("mutableStrategy.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("createPrivate()") CallDispatchHeadNode compareDispatchNode,
                @Cached CmpIntNode cmpIntNode) {
            final Object originalStore = Layouts.ARRAY.getStore(array);
            final Object store = newStoreNode.execute(getContext().getOptions().ARRAY_SMALL);
            final int size = strategy.getSize(array);

            // Copy with a exploded loop for PE

            for (int i = 0; i < getContext().getOptions().ARRAY_SMALL; i++) {
                if (i < size) {
                    setNode.execute(store, i, originalGetNode.execute(originalStore, i));
                }
            }

            // Selection sort - written very carefully to allow PE

            for (int i = 0; i < getContext().getOptions().ARRAY_SMALL; i++) {
                if (i < size) {
                    for (int j = i + 1; j < getContext().getOptions().ARRAY_SMALL; j++) {
                        if (j < size) {
                            final Object a = getNode.execute(store, i);
                            final Object b = getNode.execute(store, j);
                            final Object comparisonResult = compareDispatchNode.call(b, "<=>", a);
                            if (cmpIntNode.executeCmpInt(comparisonResult, b, a) < 0) {
                                setNode.execute(store, j, a);
                                setNode.execute(store, i, b);
                            }
                        }
                    }
                }
            }

            return createArray(store, size);
        }

        @Specialization(
                guards = {
                        "!isEmptyArray(array)",
                        "!isSmall(array)",
                        "strategy.matches(array)",
                        "strategy.isPrimitive()" },
                assumptions = {
                        "getContext().getCoreMethods().integerCmpAssumption",
                        "getContext().getCoreMethods().floatCmpAssumption" })
        protected Object sortPrimitiveArrayNoBlock(DynamicObject array, NotProvided block,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("strategy.generalizeForMutation()") ArrayStrategy mutableStrategy,
                @Cached("mutableStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("strategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode,
                @Cached("mutableStrategy.sortNode()") ArrayOperationNodes.ArraySortNode sortNode) {
            final int size = getSize(array);
            Object oldStore = Layouts.ARRAY.getStore(array);
            Object newStore = newStoreNode.execute(size);
            copyToNode.execute(oldStore, newStore, 0, 0, size);
            sortNode.execute(newStore, size);
            return createArray(newStore, size);
        }

        @Specialization(
                guards = { "!isEmptyArray(array)", "!isSmall(array)", "strategy.matches(array)" },
                limit = "STORAGE_STRATEGIES")
        protected Object sortArrayWithoutBlock(DynamicObject array, NotProvided block,
                @Cached("createPrivate()") CallDispatchHeadNode fallbackNode,
                @Cached("of(array)") ArrayStrategy strategy) {
            return fallbackNode.call(array, "sort_fallback");
        }

        @Specialization(guards = "!isEmptyArray(array)")
        protected Object sortGenericWithBlock(DynamicObject array, DynamicObject block,
                @Cached("createPrivate()") CallDispatchHeadNode fallbackNode) {
            return fallbackNode.callWithBlock(array, "sort_fallback", block);
        }

        protected boolean isSmall(DynamicObject array) {
            return getSize(array) <= getContext().getOptions().ARRAY_SMALL;
        }

    }

    @Primitive(name = "steal_array_storage")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StealArrayStorageNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "array == other")
        protected DynamicObject stealStorageNoOp(DynamicObject array, DynamicObject other) {
            return array;
        }

        @Specialization(
                guards = { "array != other", "strategy.matches(array)", "otherStrategy.matches(other)" },
                limit = "ARRAY_STRATEGIES")
        protected DynamicObject stealStorage(DynamicObject array, DynamicObject other,
                @Cached("of(array)") ArrayStrategy strategy,
                @Cached("of(other)") ArrayStrategy otherStrategy,
                @Cached PropagateSharingNode propagateSharingNode) {
            propagateSharingNode.propagate(array, other);

            final int size = getSize(other);
            final Object store = getStore(other);
            strategy.setStoreAndSize(array, store, size);
            otherStrategy.setStoreAndSize(other, ArrayStrategy.NULL_ARRAY_STORE, 0);

            return array;
        }

    }

    @Primitive(name = "array_zip")
    @ImportStatic(ArrayGuards.class)
    @ReportPolymorphism
    public abstract static class ZipNode extends PrimitiveArrayArgumentsNode {

        @Specialization(
                guards = { "isRubyArray(other)", "aStrategy.matches(array)", "bStrategy.matches(other)" },
                limit = "ARRAY_STRATEGIES")
        protected DynamicObject zipToPairs(DynamicObject array, DynamicObject other,
                @Cached("of(array)") ArrayStrategy aStrategy,
                @Cached("of(other)") ArrayStrategy bStrategy,
                @Cached("aStrategy.getNode()") ArrayOperationNodes.ArrayGetNode aGetNode,
                @Cached("bStrategy.getNode()") ArrayOperationNodes.ArrayGetNode bGetNode,
                @Cached("aStrategy.generalize(bStrategy)") ArrayStrategy generalized,
                @Cached("generalized.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("generalized.setNode()") ArrayOperationNodes.ArraySetNode setNode,
                @Cached("createBinaryProfile()") ConditionProfile bNotSmallerProfile) {
            final Object a = Layouts.ARRAY.getStore(array);
            final Object b = Layouts.ARRAY.getStore(other);

            final int bSize = bStrategy.getSize(other);
            final int zippedLength = aStrategy.getSize(array);
            final Object[] zipped = new Object[zippedLength];

            for (int n = 0; n < zippedLength; n++) {
                if (bNotSmallerProfile.profile(n < bSize)) {
                    final Object pair = newStoreNode.execute(2);
                    setNode.execute(pair, 0, aGetNode.execute(a, n));
                    setNode.execute(pair, 1, bGetNode.execute(b, n));
                    zipped[n] = createArray(pair, 2);
                } else {
                    zipped[n] = createArray(new Object[]{ aGetNode.execute(a, n), nil() }, 2);
                }
            }

            return createArray(zipped, zippedLength);
        }

    }

    @Primitive(name = "array_store_to_native")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StoreToNativeNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = {
                "oldStrategy.matches(array)",
                "oldStrategy != nativeStrategy"
        }, limit = "ARRAY_STRATEGIES")
        protected DynamicObject storeToNative(DynamicObject array,
                @Cached("of(array)") ArrayStrategy oldStrategy,
                @Cached("nativeStrategy()") ArrayStrategy nativeStrategy,
                @Cached("nativeStrategy.newStoreNode()") ArrayOperationNodes.ArrayNewStoreNode newStoreNode,
                @Cached("oldStrategy.copyToNode()") ArrayOperationNodes.ArrayCopyToNode copyToNode) {
            int size = oldStrategy.getSize(array);
            Object oldStore = Layouts.ARRAY.getStore(array);
            Object newStore = newStoreNode.execute(size);
            copyToNode.execute(oldStore, newStore, 0, 0, size);
            getContext().getMarkingService().addMarker(
                    newStore,
                    (aStore) -> ((NativeArrayStorage) aStore).preserveMembers());
            Layouts.ARRAY.setStore(array, newStore);
            return array;
        }

        @Specialization(guards = {
                "oldStrategy.matches(array)",
                "oldStrategy == nativeStrategy"
        }, limit = "ARRAY_STRATEGIES")
        protected DynamicObject storeIsNative(DynamicObject array,
                @Cached("of(array)") ArrayStrategy oldStrategy,
                @Cached("nativeStrategy()") ArrayStrategy nativeStrategy) {
            return array;
        }
    }

    @Primitive(name = "array_store_address")
    @ImportStatic(ArrayGuards.class)
    public abstract static class StoreAddressNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = {
                "oldStrategy.matches(array)",
                "oldStrategy == nativeStrategy"
        }, limit = "ARRAY_STRATEGIES")
        protected long storeIsNative(DynamicObject array,
                @Cached("of(array)") ArrayStrategy oldStrategy,
                @Cached("nativeStrategy()") ArrayStrategy nativeStrategy) {
            NativeArrayStorage storage = (NativeArrayStorage) Layouts.ARRAY.getStore(array);
            return storage.getAddress();
        }
    }
}
