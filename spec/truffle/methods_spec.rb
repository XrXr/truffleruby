# Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

# Switch to MRI, the version we are compatible with, and run:
# $ jt test spec/truffle/methods_spec.rb -t ruby
# to regenerate the files under methods/.

# jt test and jt tag can be used as normal,
# but instead of jt untag, jt purge must be used to remove tags:
# $ jt purge spec/truffle/methods_spec.rb

modules = %w[
  BasicObject Kernel Object
  Module Class
  Enumerable Enumerator Enumerator::Lazy Range
  Numeric Integer Float
  Rational Complex
  Array Hash String
  File IO
  ENV.singleton_class
]

guard -> { !defined?(SlowSpecsTagger) } do
  if RUBY_ENGINE == "ruby"
    modules.each do |mod|
      file = File.expand_path("../methods/#{mod}.txt", __FILE__)
      methods = ruby_exe("puts #{mod}.public_instance_methods(false).sort")
      methods = methods.lines.map { |line| line.chomp.to_sym }
      contents = methods.map { |meth| "#{meth}\n" }.join
      File.write file, contents
    end
  end

  code = <<-EOR
  #{modules.inspect}.each { |m|
    puts m
    puts eval(m).public_instance_methods(false).sort
    puts
  }
  EOR
  all_methods = {}
  ruby_exe(code).rstrip.split("\n\n").each do |group|
    mod, *methods = group.lines.map(&:chomp)
    all_methods[mod] = methods.map(&:to_sym)
  end

  modules.each do |mod|
    describe "Public methods on #{mod}" do
      file = File.expand_path("../methods/#{mod}.txt", __FILE__)
      expected = File.readlines(file).map { |line| line.chomp.to_sym }
      methods = all_methods[mod]

      if methods == expected
        it "are the same as on MRI" do
          methods.should == expected
        end
      else
        (methods - expected).each do |extra|
          it "should not include #{extra}" do
            methods.should_not include(extra)
          end
        end
        (expected - methods).each do |missing|
          it "should include #{missing}" do
            methods.should include(missing)
          end
        end
      end
    end
  end
end
