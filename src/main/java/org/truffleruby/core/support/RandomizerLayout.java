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

import org.truffleruby.algorithms.Randomizer;
import org.truffleruby.core.basicobject.BasicObjectLayout;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;

@Layout
public interface RandomizerLayout extends BasicObjectLayout {

    DynamicObjectFactory createRandomizerShape(DynamicObject logicalClass,
            DynamicObject metaClass);

    DynamicObject createRandomizer(DynamicObjectFactory factory,
            Randomizer randomizer);

    Randomizer getRandomizer(DynamicObject object);

    void setRandomizer(DynamicObject object, Randomizer value);

}
