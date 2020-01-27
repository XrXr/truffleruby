/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.mutex;

import java.util.concurrent.locks.ReentrantLock;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.builtins.YieldingCoreMethodNode;
import org.truffleruby.core.cast.DurationToMillisecondsNodeGen;
import org.truffleruby.core.kernel.KernelNodes;
import org.truffleruby.core.thread.GetCurrentRubyThreadNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocateObjectNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreModule(value = "Mutex", isClass = true)
public abstract class MutexNodes {

    @CoreMethod(names = { "__allocate__", "__layout_allocate__" }, constructor = true, visibility = Visibility.PRIVATE)
    public abstract static class AllocateNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateObjectNode allocateNode = AllocateObjectNode.create();

        @Specialization
        protected DynamicObject allocate(DynamicObject rubyClass) {
            return allocateNode.allocate(rubyClass, new ReentrantLock());
        }

    }

    @CoreMethod(names = "lock")
    public abstract static class LockNode extends UnaryCoreMethodNode {

        @Specialization
        protected DynamicObject lock(VirtualFrame frame, DynamicObject mutex,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached BranchProfile errorProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);

            if (lock.isHeldByCurrentThread()) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().threadErrorRecursiveLocking(this));
            }

            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);
            MutexOperations.lock(getContext(), lock, thread, this);
            return mutex;
        }

    }

    @CoreMethod(names = "locked?")
    public abstract static class IsLockedNode extends UnaryCoreMethodNode {

        @Specialization
        protected boolean isLocked(DynamicObject mutex) {
            return Layouts.MUTEX.getLock(mutex).isLocked();
        }

    }

    @CoreMethod(names = "owned?")
    public abstract static class IsOwnedNode extends UnaryCoreMethodNode {

        @Specialization
        protected boolean isOwned(DynamicObject mutex) {
            return Layouts.MUTEX.getLock(mutex).isHeldByCurrentThread();
        }

    }

    @CoreMethod(names = "try_lock")
    public abstract static class TryLockNode extends UnaryCoreMethodNode {

        @Specialization
        protected boolean tryLock(VirtualFrame frame, DynamicObject mutex,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached("createBinaryProfile()") ConditionProfile heldByCurrentThreadProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            if (heldByCurrentThreadProfile.profile(lock.isHeldByCurrentThread())) {
                return false;
            } else {
                return doTryLock(thread, lock);
            }
        }

        @TruffleBoundary
        private boolean doTryLock(DynamicObject thread, ReentrantLock lock) {
            if (lock.tryLock()) {
                Layouts.THREAD.getOwnedLocks(thread).add(lock);
                return true;
            } else {
                return false;
            }
        }

    }

    @CoreMethod(names = "unlock")
    public abstract static class UnlockNode extends UnaryCoreMethodNode {

        @Specialization
        protected DynamicObject unlock(VirtualFrame frame, DynamicObject mutex,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached BranchProfile errorProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            MutexOperations.checkOwnedMutex(getContext(), lock, this, errorProfile);
            MutexOperations.unlock(lock, thread);
            return mutex;
        }

    }

    @CoreMethod(names = "synchronize", needsBlock = true)
    public abstract static class SynchronizeNode extends YieldingCoreMethodNode {

        @Specialization
        protected Object synchronize(VirtualFrame frame, DynamicObject mutex, DynamicObject block,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached BranchProfile errorProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            if (lock.isHeldByCurrentThread()) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().threadErrorRecursiveLocking(this));
            }

            MutexOperations.lock(getContext(), lock, thread, this);
            try {
                return yield(block);
            } finally {
                MutexOperations.checkOwnedMutex(getContext(), lock, this, errorProfile);
                MutexOperations.unlock(lock, thread);
            }
        }

    }

    @NodeChild(value = "mutex", type = RubyNode.class)
    @NodeChild(value = "duration", type = RubyNode.class)
    @CoreMethod(names = "sleep", optional = 1)
    public abstract static class SleepNode extends CoreMethodNode {

        @CreateCast("duration")
        protected RubyNode coerceDuration(RubyNode duration) {
            return DurationToMillisecondsNodeGen.create(true, duration);
        }

        @Specialization
        protected long sleep(VirtualFrame frame, DynamicObject mutex, long durationInMillis,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached BranchProfile errorProfile) {
            final ReentrantLock lock = Layouts.MUTEX.getLock(mutex);
            final DynamicObject thread = getCurrentRubyThreadNode.executeGetRubyThread(frame);

            MutexOperations.checkOwnedMutex(getContext(), lock, this, errorProfile);

            /*
             * Clear the wakeUp flag, following Ruby semantics:
             * it should only be considered if we are inside the sleep when Thread#{run,wakeup} is called.
             * Here we do it before unlocking for providing nice semantics for
             * thread1: mutex.sleep
             * thread2: mutex.synchronize { <ensured that thread1 is sleeping and thread1.wakeup will wake it up> }
             */

            Layouts.THREAD.getWakeUp(thread).set(false);

            MutexOperations.unlock(lock, thread);
            try {
                return KernelNodes.SleepNode.sleepFor(this, getContext(), thread, durationInMillis);
            } finally {
                MutexOperations.lockEvenWithExceptions(lock, thread, this);
            }
        }

    }

}
