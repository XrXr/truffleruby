# Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Graal.bailout" do

  it "raises a RuntimeError when called dynamically" do
    -> { Truffle::Graal.send(:bailout, "message") }.should raise_error(RuntimeError)
  end

  guard -> { !TruffleRuby.jit? } do
    it "returns nil" do
      Truffle::Graal.bailout("message").should be_nil
    end
  end

end
