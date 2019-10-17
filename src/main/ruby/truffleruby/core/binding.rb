# frozen_string_literal: true

# Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Binding
  def eval(code, file = nil, line = nil)
    if file.nil? && line.nil?
      file, line = source_location
    end
    Kernel.eval(code, self, file, line)
  end

  def local_variables
    TrufflePrimitive.local_variable_names(self).dup
  end
  Truffle::Graal.always_split(method(:local_variables))

  def irb
    require 'irb'
    irb
  end
end
