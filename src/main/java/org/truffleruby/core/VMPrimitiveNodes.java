/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 * Some of the code in this class is transliterated from C++ code in Rubinius.
 *
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.truffleruby.core;

import java.io.PrintStream;
import java.util.Map.Entry;

import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.core.basicobject.BasicObjectNodes.ReferenceEqualNode;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes.MakeStringNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.control.ExitException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.ThrowException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.language.yield.YieldNode;
import org.truffleruby.platform.Signals;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreModule(value = "VMPrimitives", isClass = true)
public abstract class VMPrimitiveNodes {

    @Primitive(name = "vm_catch")
    public abstract static class CatchNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object doCatch(Object tag, DynamicObject block,
                @Cached BranchProfile catchProfile,
                @Cached("createBinaryProfile()") ConditionProfile matchProfile,
                @Cached ReferenceEqualNode referenceEqualNode,
                @Cached YieldNode dispatchNode) {
            try {
                return dispatchNode.executeDispatch(block, tag);
            } catch (ThrowException e) {
                catchProfile.enter();
                if (matchProfile.profile(referenceEqualNode.executeReferenceEqual(e.getTag(), tag))) {
                    return e.getValue();
                } else {
                    throw e;
                }
            }
        }
    }

    // The hard #exit!
    @Primitive(name = "vm_exit", lowerFixnum = 0)
    public static abstract class VMExitNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object vmExit(int status) {
            throw new ExitException(status);
        }

    }

    @Primitive(name = "vm_extended_modules")
    public static abstract class VMExtendedModulesNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object vmExtendedModules(Object object, DynamicObject block,
                @Cached MetaClassNode metaClassNode,
                @Cached YieldNode yieldNode,
                @Cached("createBinaryProfile()") ConditionProfile isSingletonProfile) {
            final DynamicObject metaClass = metaClassNode.executeMetaClass(object);

            if (isSingletonProfile.profile(Layouts.CLASS.getIsSingleton(metaClass))) {
                for (DynamicObject included : Layouts.MODULE.getFields(metaClass).prependedAndIncludedModules()) {
                    yieldNode.executeDispatch(block, included);
                }
            }

            return nil();
        }

    }

    @Primitive(name = "vm_method_is_basic")
    public static abstract class VMMethodIsBasicNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected boolean vmMethodIsBasic(VirtualFrame frame, DynamicObject method) {
            return Layouts.METHOD.getMethod(method).isBuiltIn();
        }

    }

    @Primitive(name = "vm_method_lookup")
    public static abstract class VMMethodLookupNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject vmMethodLookup(VirtualFrame frame, Object receiver, Object name,
                @Cached NameToJavaStringNode nameToJavaStringNode,
                @Cached LookupMethodNode lookupMethodNode) {
            // TODO BJF Sep 14, 2016 Handle private
            final String normalizedName = nameToJavaStringNode.executeToJavaString(name);
            InternalMethod method = lookupMethodNode.lookupIgnoringVisibility(frame, receiver, normalizedName);
            if (method == null) {
                return nil();
            }
            return Layouts.METHOD.createMethod(coreLibrary().methodFactory, receiver, method);
        }

    }

    @Primitive(name = "vm_raise_exception")
    public static abstract class VMRaiseExceptionNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyException(exception)")
        protected DynamicObject vmRaiseException(DynamicObject exception, boolean internal,
                @Cached("createBinaryProfile()") ConditionProfile reRaiseProfile) {
            final Backtrace backtrace = Layouts.EXCEPTION.getBacktrace(exception);
            if (reRaiseProfile.profile(backtrace != null && backtrace.getRaiseException() != null)) {
                // We need to rethrow the existing RaiseException, otherwise we would lose the
                // TruffleStackTrace stored in it.
                assert backtrace.getRaiseException().getException() == exception;
                throw backtrace.getRaiseException();
            } else {
                throw new RaiseException(getContext(), exception, internal);
            }
        }

        public static void reRaiseException(RubyContext context, DynamicObject exception) {
            final Backtrace backtrace = Layouts.EXCEPTION.getBacktrace(exception);
            if (backtrace != null && backtrace.getRaiseException() != null) {
                // We need to rethrow the existing RaiseException, otherwise we would lose the
                // TruffleStackTrace stored in it.
                throw backtrace.getRaiseException();
            } else {
                throw new RaiseException(context, exception, false);
            }
        }

    }

    @Primitive(name = "vm_set_module_name")
    public static abstract class VMSetModuleNameNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object vmSetModuleName(Object object) {
            throw new UnsupportedOperationException("vm_set_module_name");
        }

    }

    @Primitive(name = "vm_throw")
    public abstract static class ThrowNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object doThrow(Object tag, Object value) {
            throw new ThrowException(tag, value);
        }

    }

    @Primitive(name = "vm_watch_signal")
    public static abstract class VMWatchSignalNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = { "isRubyString(signalName)", "isRubyString(action)" })
        protected boolean restoreDefault(DynamicObject signalName, DynamicObject action) {
            final String actionString = StringOperations.getString(action);
            final String signal = StringOperations.getString(signalName);

            switch (actionString) {
                case "DEFAULT":
                    return restoreDefaultHandler(signal);
                case "SYSTEM_DEFAULT":
                    return restoreSystemHandler(signal);
                case "IGNORE":
                    return registerIgnoreHandler(signal);
                default:
                    throw new UnsupportedOperationException(actionString);
            }
        }

        @TruffleBoundary
        @Specialization(guards = { "isRubyString(signalName)", "isRubyProc(action)" })
        protected boolean watchSignalProc(DynamicObject signalName, DynamicObject action) {
            if (getContext().getThreadManager().getCurrentThread() != getContext().getThreadManager().getRootThread()) {
                // The proc will be executed on the main thread
                SharedObjects.writeBarrier(getContext(), action);
            }

            final RubyContext context = getContext();

            final String signal = StringOperations.getString(signalName);
            return registerHandler(signal, () -> {
                if (context.getOptions().SINGLE_THREADED) {
                    RubyLanguage.LOGGER.severe(
                            "signal " + signal + " caught but can't create a thread to handle it so ignoring");
                    return;
                }

                final DynamicObject rootThread = context.getThreadManager().getRootThread();
                final FiberManager fiberManager = Layouts.THREAD.getFiberManager(rootThread);

                // Workaround: we need to register with Truffle (which means going multithreaded),
                // so that NFI can get its context to call pthread_kill() (GR-7405).
                final TruffleContext truffleContext = context.getEnv().getContext();
                final Object prev;
                try {
                    prev = truffleContext.enter();
                } catch (IllegalStateException e) { // Multi threaded access denied from Truffle
                    // Not in a context, so we cannot use TruffleLogger
                    final PrintStream printStream = new PrintStream(context.getEnv().err(), true);
                    printStream.println(
                            "[ruby] SEVERE: signal " + signal +
                                    " caught but can't create a thread to handle it so ignoring and restoring the default handler");
                    Signals.restoreDefaultHandler(signal);
                    return;
                }
                try {
                    context.getSafepointManager().pauseAllThreadsAndExecuteFromNonRubyThread(
                            true,
                            (rubyThread, currentNode) -> {
                                if (rubyThread == rootThread &&
                                        fiberManager.getRubyFiberFromCurrentJavaThread() == fiberManager
                                                .getCurrentFiber()) {
                                    ProcOperations.rootCall(action);
                                }
                            });
                } finally {
                    truffleContext.leave(prev);
                }
            });
        }

        @TruffleBoundary
        private boolean restoreDefaultHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "restoring default handler for signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                return Signals.restoreDefaultHandler(signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
        }

        @TruffleBoundary
        private boolean restoreSystemHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "restoring system handler for signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.restoreSystemHandler(signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

        @TruffleBoundary
        private boolean registerIgnoreHandler(String signalName) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "ignoring signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.registerIgnoreHandler(signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

        @TruffleBoundary
        private boolean registerHandler(String signalName, Runnable newHandler) {
            if (getContext().getOptions().EMBEDDED) {
                RubyLanguage.LOGGER.warning(
                        "trapping signal " + signalName +
                                " in embedded mode may interfere with other embedded contexts or the host system");
            }

            try {
                Signals.registerHandler(newHandler, signalName);
            } catch (IllegalArgumentException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
            return true;
        }

    }

    @Primitive(name = "vm_get_config_item")
    public abstract static class VMGetConfigItemNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubyString(key)")
        protected Object get(DynamicObject key) {
            final Object value = getContext().getNativeConfiguration().get(StringOperations.getString(key));

            if (value == null) {
                return nil();
            } else {
                return value;
            }
        }

    }

    @Primitive(name = "vm_get_config_section")
    public abstract static class VMGetConfigSectionNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = { "isRubyString(section)", "isRubyProc(block)" })
        protected DynamicObject getSection(DynamicObject section, DynamicObject block,
                @Cached MakeStringNode makeStringNode,
                @Cached YieldNode yieldNode) {
            for (Entry<String, Object> entry : getContext()
                    .getNativeConfiguration()
                    .getSection(StringOperations.getString(section))) {
                final DynamicObject key = makeStringNode
                        .executeMake(entry.getKey(), UTF8Encoding.INSTANCE, CodeRange.CR_7BIT);
                yieldNode.executeDispatch(block, key, entry.getValue());
            }

            return nil();
        }

    }

    @Primitive(name = "vm_set_class")
    public abstract static class VMSetClassNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyClass(newClass)")
        protected DynamicObject setClass(DynamicObject object, DynamicObject newClass) {
            SharedObjects.propagate(getContext(), object, newClass);
            synchronized (object) {
                Layouts.BASIC_OBJECT.setLogicalClass(object, newClass);
                Layouts.BASIC_OBJECT.setMetaClass(object, newClass);
            }
            return object;
        }

    }

    @Primitive(name = "vm_dev_urandom_bytes", lowerFixnum = 0)
    public abstract static class VMDevUrandomBytes extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "count >= 0")
        protected DynamicObject readRandomBytes(int count,
                @Cached MakeStringNode makeStringNode) {
            final byte[] bytes = getContext().getRandomSeedBytes(count);

            return makeStringNode.executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

        @Specialization(guards = "count < 0")
        protected DynamicObject negativeCount(int count) {
            throw new RaiseException(
                    getContext(),
                    getContext().getCoreExceptions().argumentError(
                            getContext().getCoreStrings().NEGATIVE_STRING_SIZE.getRope(),
                            this));
        }

    }

    @Primitive(name = "vm_hash_start")
    public abstract static class VMHashStartNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long startHash(long salt) {
            return getContext().getHashing(this).start(salt);
        }

        @Specialization(guards = "isRubyBignum(salt)")
        protected long startHashBigNum(DynamicObject salt) {
            return getContext().getHashing(this).start(Layouts.BIGNUM.getValue(salt).hashCode());
        }

        @Specialization(guards = "!isRubyNumber(salt)")
        protected Object startHashNotNumber(Object salt,
                @Cached("createPrivate()") CallDispatchHeadNode coerceToIntNode,
                @Cached("createBinaryProfile()") ConditionProfile isIntegerProfile,
                @Cached("createBinaryProfile()") ConditionProfile isLongProfile,
                @Cached("createBinaryProfile()") ConditionProfile isBignumProfile) {
            Object result = coerceToIntNode.call(coreLibrary().truffleTypeModule, "coerce_to_int", salt);
            if (isIntegerProfile.profile(result instanceof Integer)) {
                return getContext().getHashing(this).start((int) result);
            } else if (isLongProfile.profile(result instanceof Long)) {
                return getContext().getHashing(this).start((long) result);
            } else if (isBignumProfile.profile(Layouts.BIGNUM.isBignum(result))) {
                return getContext().getHashing(this).start(Layouts.BIGNUM.getValue((DynamicObject) result).hashCode());
            } else {
                throw new UnsupportedOperationException();
            }

        }
    }

    @Primitive(name = "vm_hash_update")
    public abstract static class VMHashUpdateNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long updateHash(long hash, long value) {
            return Hashing.update(hash, value);
        }

        @Specialization(guards = "isRubyBignum(value)")
        protected long updateHash(long hash, DynamicObject value) {
            return Hashing.update(hash, Layouts.BIGNUM.getValue(value).hashCode());
        }

        @Specialization(guards = "!isRubyNumber(value)")
        protected Object updateHash(long hash, Object value,
                @Cached("createPrivate()") CallDispatchHeadNode coerceToIntNode,
                @Cached("createBinaryProfile()") ConditionProfile isIntegerProfile,
                @Cached("createBinaryProfile()") ConditionProfile isLongProfile,
                @Cached("createBinaryProfile()") ConditionProfile isBignumProfile) {
            Object result = coerceToIntNode.call(coreLibrary().truffleTypeModule, "coerce_to_int", value);
            if (isIntegerProfile.profile(result instanceof Integer)) {
                return Hashing.update(hash, (int) result);
            } else if (isLongProfile.profile(result instanceof Long)) {
                return Hashing.update(hash, (long) result);
            } else if (isBignumProfile.profile(Layouts.BIGNUM.isBignum(result))) {
                return Hashing.update(hash, Layouts.BIGNUM.getValue((DynamicObject) result).hashCode());
            } else {
                throw new UnsupportedOperationException();
            }

        }
    }

    @Primitive(name = "vm_hash_end")
    public abstract static class VMHashEndNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long endHash(long hash) {
            return Hashing.end(hash);
        }

    }

}
