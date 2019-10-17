/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 *
 *
 * Some of the code in this class is modified from org.jruby.util.StringSupport,
 * licensed under the same EPL 2.0/GPL 2.0/LGPL 2.1 used throughout.
 */
package org.truffleruby.aot;

import java.nio.file.SimpleFileVisitor;

public class SimpleRootedFileVisitor<T> extends SimpleFileVisitor<T> implements RootedFileVisitor<T> {
    private T root;

    @Override
    public void setRoot(T root) {
        this.root = root;
    }

    @Override
    public T getRoot() {
        return root;
    }
}
