/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.core.array.ArrayGuards;
import org.truffleruby.core.array.ArrayOperationNodes;
import org.truffleruby.core.array.ArrayStrategy;
import org.truffleruby.core.binding.BindingNodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.object.DynamicObject;

@CoreModule("Truffle::ThreadOperations")
public class TruffleThreadNodes {

    @CoreMethod(names = "ruby_caller", onSingleton = true, required = 1)
    @ImportStatic(ArrayGuards.class)
    public abstract static class FindRubyCaller extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = { "isRubyArray(modules)", "strategy.matches(modules)" })
        protected DynamicObject findRubyCaller(DynamicObject modules,
                @Cached("of(modules)") ArrayStrategy strategy,
                @Cached("strategy.boxedCopyNode()") ArrayOperationNodes.ArrayBoxedCopyNode boxedCopyNode) {
            Object[] moduleArray = boxedCopyNode
                    .execute(Layouts.ARRAY.getStore(modules), Layouts.ARRAY.getSize(modules));
            Frame rubyCaller = getContext()
                    .getCallStack()
                    .getCallerFrameNotInModules(FrameAccess.MATERIALIZE, moduleArray);
            if (rubyCaller == null) {
                return nil();
            } else {
                return BindingNodes.createBinding(getContext(), rubyCaller.materialize());
            }
        }

    }
}
