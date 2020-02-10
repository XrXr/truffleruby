# Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Foreign arrays" do

  it "implement #to_s with #inspect" do
    foreign = Truffle::Interop.to_java_array([1, 2, 3])
    foreign.to_s.should == foreign.inspect
  end

  it "can be printed with #puts" do
    -> {
      puts Truffle::Interop.to_java_array([1, 2, 3])
    }.should output_to_fd("1\n2\n3\n")
  end

  it "can be printed with #p" do
    -> {
      p Truffle::Interop.to_java_array([1, 2, 3])
    }.should output_to_fd(/#<Java:0x\h+ \[1, 2, 3\]>\n/)
  end

end

describe "Foreign arrays that are also pointers" do
  it "implement #to_s with #inspect" do
    foreign = Truffle::Debug.foreign_pointer_array_from_java(Truffle::Interop.to_java_array([1, 2, 3]))
    foreign.to_s.should == foreign.inspect
  end

  it "can be printed with #puts" do
    -> {
      puts Truffle::Debug.foreign_pointer_array_from_java(Truffle::Interop.to_java_array([1, 2, 3]))
    }.should output_to_fd("1\n2\n3\n")
  end

  it "can be printed with #p" do
    -> {
      p Truffle::Debug.foreign_pointer_array_from_java(Truffle::Interop.to_java_array([1, 2, 3]))
    }.should output_to_fd(/#<Foreign pointer 0x\h+ \[1, 2, 3\]>\n/)
  end

  it "can be printed with #print" do
    -> {
      print Truffle::Debug.foreign_pointer_array_from_java(Truffle::Interop.to_java_array([1, 2, 3]))
    }.should output_to_fd(/#<Foreign pointer 0x\h+ \[1, 2, 3\]>/)
  end
end
