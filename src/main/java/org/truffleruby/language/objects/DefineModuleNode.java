/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects;

import org.truffleruby.core.module.ModuleNodes;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "lexicalParentModule", type = RubyNode.class)
public abstract class DefineModuleNode extends RubyContextSourceNode {

    private final String name;

    @Child LookupForExistingModuleNode lookupForExistingModuleNode;

    private final ConditionProfile needToDefineProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorProfile = BranchProfile.create();

    public DefineModuleNode(String name) {
        this.name = name;
    }

    @Specialization(guards = "isRubyModule(lexicalParentModule)")
    protected Object defineModule(VirtualFrame frame, DynamicObject lexicalParentModule) {
        final Object existing = lookupForExistingModule(frame, name, lexicalParentModule);

        final DynamicObject definingModule;

        if (needToDefineProfile.profile(existing == null)) {
            definingModule = ModuleNodes.createModule(
                    getContext(),
                    getEncapsulatingSourceSection(),
                    coreLibrary().moduleClass,
                    lexicalParentModule,
                    name,
                    this);
        } else {
            if (!RubyGuards.isRubyModule(existing) || RubyGuards.isRubyClass(existing)) {
                errorProfile.enter();
                throw new RaiseException(getContext(), coreExceptions().typeErrorIsNotA(name, "module", this));
            }

            definingModule = (DynamicObject) existing;
        }

        return definingModule;
    }

    @Specialization(guards = "!isRubyModule(lexicalParentObject)")
    protected Object defineModuleWrongParent(VirtualFrame frame, Object lexicalParentObject) {
        throw new RaiseException(getContext(), coreExceptions().typeErrorIsNotA(lexicalParentObject, "module", this));
    }

    private Object lookupForExistingModule(VirtualFrame frame, String name, DynamicObject lexicalParent) {
        if (lookupForExistingModuleNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupForExistingModuleNode = insert(new LookupForExistingModuleNode());
        }
        return lookupForExistingModuleNode.lookupForExistingModule(frame, name, lexicalParent);
    }

}
