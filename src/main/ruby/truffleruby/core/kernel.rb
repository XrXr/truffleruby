# frozen_string_literal: true

# Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Kernel
  def Array(obj)
    ary = Truffle::Type.rb_check_convert_type obj, Array, :to_ary

    return ary if ary

    if array = Truffle::Type.rb_check_convert_type(obj, Array, :to_a)
      array
    else
      [obj]
    end
  end
  module_function :Array

  def Complex(*args)
    Complex.__send__ :convert, *args
  end
  module_function :Complex

  def Float(obj, exception: true)
    raise_exception = !exception.equal?(false)
    obj = Truffle::Interop.unbox_if_needed(obj)

    case obj
    when String
      Primitive.string_to_f obj, raise_exception
    when Float
      obj
    when nil
      if raise_exception
        raise TypeError, "can't convert nil into Float"
      else
        nil
      end
    when Complex
      if obj.respond_to?(:imag) && obj.imag.equal?(0)
        Truffle::Type.coerce_to obj, Float, :to_f
      else
        raise RangeError, "can't convert #{obj} into Float"
      end
    else
      if raise_exception
        Truffle::Type.rb_convert_type(obj, Float, :to_f)
      else
        Truffle::Type.rb_check_convert_type(obj, Float, :to_f)
      end
    end
  end
  module_function :Float

  def Hash(obj)
    return {} if obj.equal?(nil) || obj == []

    if hash = Truffle::Type.rb_check_convert_type(obj, Hash, :to_hash)
      return hash
    end

    raise TypeError, "can't convert #{Truffle::Type.object_class(obj)} into Hash"
  end
  module_function :Hash

  def Integer(obj, base=0, exception: true)
    obj = Truffle::Interop.unbox_if_needed(obj)
    converted_base = Truffle::Type.rb_check_to_integer(base, :to_int)
    base = converted_base.nil? ? 0 : converted_base
    raise_exception = !exception.equal?(false)

    if String === obj
      Primitive.string_to_inum(obj, base, true, !exception.equal?(false))
    else
      bad_base_check = Proc.new do
        if base != 0
          return nil unless raise_exception
          raise ArgumentError, 'base is only valid for String values'
        end
      end
      case obj
      when Integer
        bad_base_check.call
        obj
      when Float
        bad_base_check.call
        if obj.nan? or obj.infinite?
          return nil unless raise_exception
        end
        # TODO BJF 14-Jan-2020 Add fixable conversion logic
        obj.to_int
      when NilClass
        bad_base_check.call
        return nil unless raise_exception
        raise TypeError, "can't convert nil into Integer"
      else
        if base != 0
          converted_to_str_obj = Truffle::Type.rb_check_convert_type(obj, String, :to_str)
          return Primitive.string_to_inum(converted_to_str_obj, base, true, raise_exception) unless converted_to_str_obj.nil?
          return nil unless raise_exception
          raise ArgumentError, 'base is only valid for String values'
        end
        converted_to_int_obj = Truffle::Type.rb_check_to_integer(obj, :to_int)
        return converted_to_int_obj unless converted_to_int_obj.nil?

        return Truffle::Type.rb_check_to_integer(obj, :to_i) unless raise_exception
        Truffle::Type.rb_convert_type(obj, Integer, :to_i)
      end
    end
  end
  module_function :Integer

  def Rational(a, b = 1)
    Rational.__send__ :convert, a, b
  end
  module_function :Rational

  def String(obj)
    str = Truffle::Type.rb_check_convert_type(obj, String, :to_str)
    if str.nil?
      str = Truffle::Type.rb_convert_type(obj, String, :to_s)
    end
    str
  end
  module_function :String

  ##
  # MRI uses a macro named StringValue which has essentially the same
  # semantics as Truffle::Type.rb_convert_type obj, String, :to_str, but rather than using that
  # long construction everywhere, we define a private method similar to
  # String().

  def StringValue(obj)
    Truffle::Type.rb_convert_type obj, String, :to_str
  end
  module_function :StringValue

  def `(str) #`
    str = StringValue(str) unless str.kind_of?(String)

    output = IO.popen(str) { |io| io.read }

    Truffle::Type.external_string output
  end
  module_function :` # `

  def =~(other)
    warn "deprecated Object#=~ is called on #{self.class}; it always returns nil", uplevel: 1 if $VERBOSE
    nil
  end

  def !~(other)
    r = self =~ other ? false : true
    Truffle::RegexpOperations.set_last_match($~, Primitive.caller_binding)
    r
  end

  def itself
    self
  end

  def abort(msg=nil)
    Process.abort msg
  end
  module_function :abort

  def autoload(name, file)
    nesting = Primitive.caller_binding.eval('Module.nesting')
    mod = nesting.first || (Kernel.equal?(self) ? Kernel : Object)
    if mod.equal?(self)
      super(name, file) # Avoid recursion
    else
      mod.autoload(name, file)
    end
  end
  module_function :autoload

  def autoload?(name)
    if Kernel.equal?(self)
      super(name) # Avoid recursion
    else
      Object.autoload?(name)
    end
  end
  module_function :autoload?

  alias_method :iterator?, :block_given?

  def define_singleton_method(*args, &block)
    singleton_class.define_method(*args, &block)
  end

  def display(port=$>)
    port.write self
  end

  def eval(str, a_binding=nil, file=nil, line=nil)
    file = '(eval)' unless file
    line = 1 unless line
    str = str.to_str unless str.class == String
    file = file.to_str unless file.class == String
    line = line.to_i unless line.is_a?(Integer)
    if a_binding
      unless a_binding.class == Binding
        raise TypeError, "Wrong argument type #{a_binding.class} (expected binding)"
      end
      receiver = a_binding.receiver
    else
      receiver = self
      a_binding = Primitive.caller_binding
    end

    Primitive.kernel_eval(receiver, str, a_binding, file, line)
  end
  module_function :eval

  # It is important that eval is always cloned so that the primitive
  # inside can be specialised efficiently.
  Truffle::Graal.always_split(method(:eval))

  def exec(*args)
    Process.exec(*args)
  end
  module_function :exec

  def exit(code=0)
    Process.exit(code)
  end
  module_function :exit

  def exit!(code=1)
    Process.exit!(code)
  end
  module_function :exit!

  def extend(*modules)
    raise ArgumentError, 'wrong number of arguments (0 for 1+)' if modules.empty?

    modules.reverse_each do |mod|
      if !mod.kind_of?(Module) or mod.kind_of?(Class)
        raise TypeError, "wrong argument type #{mod.class} (expected Module)"
      end

      mod.__send__ :extend_object, self
      mod.__send__ :extended, self
    end
    self
  end

  def getc
    $stdin.getc
  end
  module_function :getc

  def gets(*args)
    line = ARGF.gets(*args)
    Truffle::IOOperations.set_last_line(line, Primitive.caller_binding) if line
    line
  end
  module_function :gets

  def inspect
    prefix = "#<#{self.class}:0x#{self.__id__.to_s(16)}"

    ivars = Primitive.object_ivars self

    if ivars.empty?
      return Primitive.infect "#{prefix}>", self
    end

    # If it's already been inspected, return the ...
    return "#{prefix} ...>" if Thread.guarding? self

    parts = Thread.recursion_guard self do
      ivars.map do |var|
        value = Primitive.object_ivar_get self, var
        "#{var}=#{value.inspect}"
      end
    end

    str = "#{prefix} #{parts.join(', ')}>"
    Primitive.infect str, self
  end

  def load(filename, wrap = false)
    filename = Truffle::Type.coerce_to_path filename

    # load absolute path
    if filename.start_with? File::SEPARATOR
      return Truffle::KernelOperations.load File.expand_path(filename), wrap
    end

    # if path starts with . only try relative paths
    if filename.start_with? '.'
      return Truffle::KernelOperations.load File.expand_path(filename), wrap
    end

    # try to resolve with current working directory
    if File.exist? filename
      return Truffle::KernelOperations.load File.expand_path(filename), wrap
    end

    # try to find relative path in $LOAD_PATH
    $LOAD_PATH.each do |dir|
      path = File.expand_path(File.join(dir, filename))
      if File.exist? path
        return Truffle::KernelOperations.load path, wrap
      end
    end

    # file not found trigger an error
    Truffle::KernelOperations.load filename, wrap
  end
  module_function :load

  def local_variables
    Primitive.caller_binding.local_variables
  end
  module_function :local_variables
  Truffle::Graal.always_split(method(:local_variables))

  def loop
    return to_enum(:loop) { Float::INFINITY } unless block_given?

    begin
      while true # rubocop:disable Lint/LiteralAsCondition
        yield
      end
    rescue StopIteration => si
      si.result
    end
  end
  module_function :loop

  def open(obj, *rest, &block)
    if obj.respond_to?(:to_open)
      obj = obj.to_open(*rest)

      if block_given?
        return yield(obj)
      else
        return obj
      end
    end

    path = Truffle::Type.coerce_to_path obj

    if path.kind_of? String and path.start_with? '|'
      return IO.popen(path[1..-1], *rest, &block)
    end

    File.open(path, *rest, &block)
  end
  module_function :open

  # Kernel#p is in post.rb

  def print(*args)
    args.each do |obj|
      $stdout.write obj.to_s
    end
    nil
  end
  module_function :print

  def putc(int)
    $stdout.putc(int)
  end
  module_function :putc

  def puts(*a)
    $stdout.puts(*a)
    nil
  end
  module_function :puts

  def rand(limit=0)
    if limit == 0
      return Thread.current.randomizer.random_float
    end

    if limit.kind_of?(Range)
      return Thread.current.randomizer.random(limit)
    else
      limit = Integer(limit).abs

      if limit == 0
        Thread.current.randomizer.random_float
      else
        Thread.current.randomizer.random_integer(limit - 1)
      end
    end
  end
  module_function :rand

  def readline(sep=$/)
    ARGF.readline(sep)
  end
  module_function :readline

  def readlines(sep=$/)
    ARGF.readlines(sep)
  end
  module_function :readlines

  def select(*args)
    IO.select(*args)
  end
  module_function :select

  def srand(seed=undefined)
    if Primitive.undefined? seed
      seed = Thread.current.randomizer.generate_seed
    end

    seed = Truffle::Type.coerce_to seed, Integer, :to_int
    Thread.current.randomizer.swap_seed seed
  end
  module_function :srand

  def tap
    yield self
    self
  end

  def yield_self
    if block_given?
      yield self
    else
      [self].to_enum { 1 }
    end
  end

  alias_method :then, :yield_self

  def test(cmd, file1, file2=nil)
    case cmd
    when ?d
      File.directory? file1
    when ?e
      File.exist? file1
    when ?f
      File.file? file1
    when ?l
      File.symlink? file1
    when ?r
      File.readable? file1
    when ?R
      File.readable_real? file1
    when ?w
      File.writable? file1
    when ?W
      File.writable_real? file1
    when ?A
      File.atime file1
    when ?C
      File.ctime file1
    when ?M
      File.mtime file1
    else
      raise NotImplementedError, "command ?#{cmd.chr} not implemented"
    end
  end
  module_function :test

  def to_enum(method=:each, *args, &block)
    Enumerator.new(self, method, *args).tap do |enum|
      enum.__send__ :size=, block if block_given?
    end
  end
  alias_method :enum_for, :to_enum

  def trap(sig, prc=nil, &block)
    Signal.trap(sig, prc, &block)
  end
  module_function :trap

  def spawn(*args)
    Process.spawn(*args)
  end
  module_function :spawn

  def syscall(*args)
    raise NotImplementedError
  end
  module_function :syscall

  def system(*args)
    options = Truffle::Type.try_convert(args.last, Hash, :to_hash)
    exception = if options
                  args[-1] = options
                  options.delete(:exception)
                else
                  false
                end

    begin
      pid = Process.spawn(*args)
    rescue SystemCallError => e
      raise e if exception
      return nil
    end

    Process.waitpid pid
    result = $?.exitstatus == 0
    return true if result
    if exception
      # TODO  (bjfish, 9 Jan 2020): refactoring needed for more descriptive errors
      raise RuntimeError, 'command failed'
    else
      return false
    end
  end
  module_function :system

  def trace_var(name, cmd = nil, &block)
    if !cmd && !block
      raise ArgumentError,
        'The 2nd argument should be a Proc/String, alternatively use a block'
    end

    # Truffle: not yet implemented
  end
  module_function :trace_var

  def untrace_var(name, cmd=undefined)
    # Truffle: not yet implemented
  end
  module_function :untrace_var

  def warn(*messages, uplevel: undefined)
    if !$VERBOSE.nil? && !messages.empty?
      prefix = if Primitive.undefined?(uplevel)
                 +''
               else
                 uplevel = Truffle::Type.coerce_to_int(uplevel)
                 raise ArgumentError, "negative level (#{uplevel})" unless uplevel >= 0

                 caller, = caller_locations(uplevel + 1, 1)
                 if caller
                   "#{caller.path}:#{caller.lineno}: warning: "
                 else
                   +'warning: '
                 end
               end

      stringio = Truffle::StringOperations::SimpleStringIO.new(prefix)
      Truffle::IOOperations.puts(stringio, *messages)
      Warning.warn(stringio.string)
    end
    nil
  end
  module_function :warn

  def raise(exc=undefined, msg=undefined, ctx=nil)
    Truffle::KernelOperations.internal_raise exc, msg, ctx, false
  end
  module_function :raise

  alias_method :fail, :raise
  module_function :fail

  def __dir__
    path = caller_locations(1, 1).first.absolute_path
    File.dirname(path)
  end
  module_function :__dir__

  def printf(*args)
    return nil if args.empty?
    if Primitive.object_kind_of?(args[0], String)
      print sprintf(*args)
    else
      io = args.shift
      io.write(sprintf(*args))
    end
    nil
  end
  module_function :printf

  private def pp(*args)
    require 'pp'
    pp(*args)
  end

  alias_method :trust, :untaint
  alias_method :untrust, :taint
  alias_method :untrusted?, :tainted?

  def caller(start = 1, limit = nil)
    args = if start.is_a? Range
             if start.end == nil
               [start.begin + 1]
             else
               [start.begin + 1, start.size]
             end
           elsif limit.nil?
             [start + 1]
           else
             [start + 1, limit]
           end
    Kernel.caller_locations(*args).map(&:to_s)
  end
  module_function :caller

  def caller_locations(omit = 1, length = undefined)
    # This could be implemented as a call to Thread#backtrace_locations, but we don't do this
    # to avoid the SafepointAction overhead in the primitive call.
    if Integer === length && length < 0
      raise ArgumentError, "negative size (#{length})"
    end
    if Range === omit
      range = omit
      omit = Truffle::Type.coerce_to_int(range.begin)
      unless range.end.nil?
        end_index = Truffle::Type.coerce_to_int(range.end)
        if end_index < 0
          length = end_index
        else
          end_index += (range.exclude_end? ? 0 : 1)
          length = omit > end_index ? 0 : end_index - omit
        end
      end
    end
    Primitive.kernel_caller_locations(omit, length)
  end
  module_function :caller_locations

  def at_exit(&block)
    Truffle::KernelOperations.at_exit false, &block
  end
  module_function :at_exit

  def fork
    raise NotImplementedError, 'fork is not available'
  end
  module_function :fork
  Primitive.method_unimplement method(:fork)
  Primitive.method_unimplement nil.method(:fork)

  Truffle::Boot.delay do
    if Truffle::Boot.get_option('gets-loop')
      def chomp(separator=$/)
        last_line = Truffle::IOOperations.last_line(Primitive.caller_binding)
        result = Truffle::KernelOperations.check_last_line(last_line).chomp(separator)
        Truffle::IOOperations.set_last_line(result, Primitive.caller_binding)
        result
      end
      module_function :chomp

      def chop
        last_line = Truffle::IOOperations.last_line(Primitive.caller_binding)
        result = Truffle::KernelOperations.check_last_line(last_line).chop
        Truffle::IOOperations.set_last_line(result, Primitive.caller_binding)
        result
      end
      module_function :chop
    end
  end
end
