# Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort 'not running the GraalVM Compiler' unless TruffleRuby.jit?

def foo
  $x ||= (TrufflePrimitive.assert_not_compiled; true)
end

begin
  loop do
    y = foo
    TrufflePrimitive.assert_compilation_constant y
  end
rescue Truffle::GraalError => e
  if e.message.include? 'TrufflePrimitive.assert_compilation_constant'
    puts "correctly optimises when one right side execution"
  elsif e.message.include? 'TrufflePrimitive.assert_not_compiled'
    puts "incorrectly does not optimise when one right side execution"
    exit 1
  else
    puts e.message, 'some other error'
    exit 1
  end
end

begin
  loop do
    $x = false
    foo
  end
rescue Truffle::GraalError => e
  if e.message.include? 'TrufflePrimitive.assert_not_compiled'
    puts "correctly stops optimising when many right side executions"
  else
    puts e.message, 'some other error'
    exit 1
  end
end

begin
  $x = true
  loop do
    foo
    z = rand
    TrufflePrimitive.assert_compilation_constant z
  end
rescue Truffle::GraalError => e
  if e.message.include? 'TrufflePrimitive.assert_compilation_constant'
    puts "correctly optimises when zero right side executions"
  elsif e.message.include? 'TrufflePrimitive.assert_not_compiled'
    puts "incorrectly does not optimise when zero right side executions"
    exit 1
  else
    puts e.message, 'some other error'
    exit 1
  end
end

exit 0
