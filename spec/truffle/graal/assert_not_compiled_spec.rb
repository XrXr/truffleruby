# Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "TrufflePrimitive.assert_not_compiled" do

  it "raises a RuntimeError when called dynamically" do
    -> { tp = TrufflePrimitive; tp.assert_not_compiled }.should raise_error(NoMethodError, /syntactically/)
  end

  guard -> { !TruffleRuby.jit? } do
    it "returns nil" do
      TrufflePrimitive.assert_not_compiled.should be_nil
    end
  end

end
