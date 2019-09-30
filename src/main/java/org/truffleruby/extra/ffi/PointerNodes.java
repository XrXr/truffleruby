/*
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.extra.ffi;

import java.math.BigInteger;

import org.jcodings.specific.ASCIIEncoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreClass;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.objects.AllocateObjectNode;
import org.truffleruby.platform.NativeTypes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreClass("Truffle::FFI::Pointer")
public abstract class PointerNodes {

    public static final BigInteger TWO_POW_64 = BigInteger.valueOf(1).shiftLeft(64);

    public static void checkNull(Pointer ptr, RubyNode node, BranchProfile nullPointerProfile) {
        if (ptr.isNull()) {
            nullPointerProfile.enter();
            final RubyContext context = node.getContext();
            throw new RaiseException(
                    context,
                    context.getCoreExceptions().ffiNullPointerError("invalid memory access at address=0x0", node));
        }
    }

    private static abstract class PointerPrimitiveArrayArgumentsNode extends PrimitiveArrayArgumentsNode {

        private final BranchProfile nullPointerProfile = BranchProfile.create();

        protected void checkNull(Pointer ptr) {
            PointerNodes.checkNull(ptr, this, nullPointerProfile);
        }

    }

    @CoreMethod(names = "__allocate__", constructor = true, visibility = Visibility.PRIVATE)
    public static abstract class AllocateNode extends UnaryCoreMethodNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        protected DynamicObject allocate(DynamicObject pointerClass) {
            return allocateObjectNode.allocate(pointerClass, Pointer.NULL);
        }

    }

    @Primitive(name = "pointer_find_type_size", needsSelf = false)
    public static abstract class PointerFindTypeSizePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @TruffleBoundary
        @Specialization(guards = "isRubySymbol(type)")
        protected int findTypeSize(DynamicObject type) {
            final String typeString = Layouts.SYMBOL.getString(type);
            final int size = typeSize(typeString);
            if (size > 0) {
                return size;
            } else {
                final Object typedef = getContext()
                        .getTruffleNFI()
                        .resolveTypeRaw(getContext().getNativeConfiguration(), typeString);
                final int typedefSize = typeSize(StringOperations.getString((DynamicObject) typedef));
                assert typedefSize > 0 : typedef;
                return typedefSize;
            }
        }

        private static int typeSize(String type) {
            switch (type) {
                case "char":
                case "uchar":
                    return 1;
                case "short":
                case "ushort":
                    return 2;
                case "int":
                case "uint":
                case "int32":
                case "uint32":
                case "float":
                    return 4;
                case "long":
                case "ulong":
                case "int64":
                case "uint64":
                case "long_long":
                case "ulong_long":
                case "double":
                case "pointer":
                    return 8;
                default:
                    return -1;
            }
        }

    }

    @Primitive(name = "nativefunction_type_size", needsSelf = false, lowerFixnum = 1)
    public static abstract class NativeFunctionTypeSizePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected int typeSize(int type) {
            switch (type) {
                case NativeTypes.TYPE_CHAR:
                case NativeTypes.TYPE_UCHAR:
                    return 1;

                case NativeTypes.TYPE_SHORT:
                case NativeTypes.TYPE_USHORT:
                    return 2;

                case NativeTypes.TYPE_INT:
                case NativeTypes.TYPE_UINT:
                    return 4;

                case NativeTypes.TYPE_LONG:
                case NativeTypes.TYPE_ULONG:
                case NativeTypes.TYPE_LL:
                case NativeTypes.TYPE_ULL:
                    return 8;

                case NativeTypes.TYPE_FLOAT:
                    return 4;

                case NativeTypes.TYPE_DOUBLE:
                    return 8;

                case NativeTypes.TYPE_PTR:
                case NativeTypes.TYPE_STRPTR:
                case NativeTypes.TYPE_STRING:
                case NativeTypes.TYPE_CHARARR:
                    return 8;

                case NativeTypes.TYPE_BOOL:
                case NativeTypes.TYPE_VOID:
                case NativeTypes.TYPE_ENUM:
                case NativeTypes.TYPE_VARARGS:
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException("no type size for: " + type);
            }
        }

        @Specialization(guards = "!isInteger(type)")
        protected Object fallback(Object type) {
            return FAILURE;
        }

    }

    @CoreMethod(names = "address=", required = 1)
    public static abstract class PointerSetAddressNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected long setAddress(DynamicObject pointer, long address) {
            Layouts.POINTER.setPointer(pointer, new Pointer(address));
            return address;
        }

    }

    @CoreMethod(names = { "address", "__address__", "to_i" })
    public static abstract class PointerAddressNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected long address(DynamicObject pointer) {
            return Layouts.POINTER.getPointer(pointer).getAddress();
        }

    }

    @CoreMethod(names = "autorelease?")
    public static abstract class PointerIsAutoreleaseNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected boolean isAutorelease(DynamicObject pointer) {
            return Layouts.POINTER.getPointer(pointer).isAutorelease();
        }

    }

    @CoreMethod(names = "autorelease=", required = 1)
    public static abstract class PointerSetAutoreleaseNode extends CoreMethodArrayArgumentsNode {

        @Specialization(guards = "autorelease")
        protected boolean enableAutorelease(DynamicObject pointer, boolean autorelease) {
            Layouts.POINTER.getPointer(pointer).enableAutorelease(getContext().getFinalizationService());
            return autorelease;
        }

        @Specialization(guards = "!autorelease")
        protected boolean disableAutorelease(DynamicObject pointer, boolean autorelease) {
            Layouts.POINTER.getPointer(pointer).disableAutorelease(getContext().getFinalizationService());
            return autorelease;
        }

    }

    @Primitive(name = "pointer_malloc")
    public static abstract class PointerMallocPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject malloc(DynamicObject pointer, long size) {
            Layouts.POINTER.setPointer(pointer, Pointer.malloc(size));
            return pointer;
        }

    }

    @CoreMethod(names = "free")
    public static abstract class PointerFreeNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected DynamicObject free(DynamicObject pointer) {
            Layouts.POINTER.getPointer(pointer).free(getContext().getFinalizationService());
            return pointer;
        }

    }

    @Primitive(name = "pointer_clear", lowerFixnum = 1)
    public abstract static class PointerClearNode extends PrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyPointer(pointer)")
        protected DynamicObject clear(DynamicObject pointer, long length) {
            Layouts.POINTER.getPointer(pointer).writeBytes(0, length, (byte) 0);
            return pointer;
        }

    }

    @Primitive(name = "pointer_copy_memory")
    public static abstract class PointerCopyMemoryNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject copyMemory(long to, long from, long size) {
            final Pointer ptr = new Pointer(to);
            checkNull(ptr);
            ptr.writeBytes(0, new Pointer(from), 0, size);
            return nil();
        }

    }

    // Special reads and writes

    @Primitive(name = "pointer_read_string_to_null")
    public static abstract class PointerReadStringToNullNode extends PointerPrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode;

        @Specialization(guards = "limit == 0")
        protected DynamicObject readNullPointer(long address, long limit) {
            return allocate(
                    coreLibrary().getStringClass(),
                    Layouts.STRING.build(false, true, RopeConstants.EMPTY_ASCII_8BIT_ROPE));
        }

        @Specialization(guards = "limit != 0")
        protected DynamicObject readStringToNull(long address, long limit,
                @Cached RopeNodes.MakeLeafRopeNode makeLeafRopeNode) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), 0, limit);
            final Rope rope = makeLeafRopeNode
                    .executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN, NotProvided.INSTANCE);
            return allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, true, rope));
        }

        @Specialization(guards = "isNil(noLimit)")
        protected DynamicObject readStringToNull(long address, DynamicObject noLimit,
                @Cached RopeNodes.MakeLeafRopeNode makeLeafRopeNode) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            final byte[] bytes = ptr.readZeroTerminatedByteArray(getContext(), 0);
            final Rope rope = makeLeafRopeNode
                    .executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN, NotProvided.INSTANCE);
            return allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, true, rope));
        }

        private DynamicObject allocate(DynamicObject object, Object[] values) {
            if (allocateObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                allocateObjectNode = insert(AllocateObjectNode.create());
            }
            return allocateObjectNode.allocate(object, values);
        }

    }

    @Primitive(name = "pointer_read_bytes", lowerFixnum = 1)
    public static abstract class PointerReadBytesNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject readBytes(long address, int length,
                @Cached("createBinaryProfile()") ConditionProfile zeroProfile,
                @Cached RopeNodes.MakeLeafRopeNode makeLeafRopeNode,
                @Cached AllocateObjectNode allocateObjectNode) {
            final Pointer ptr = new Pointer(address);
            if (zeroProfile.profile(length == 0)) {
                // No need to check the pointer address if we read nothing
                return allocateObjectNode.allocate(
                        coreLibrary().getStringClass(),
                        Layouts.STRING.build(false, false, RopeConstants.EMPTY_ASCII_8BIT_ROPE));
            } else {
                checkNull(ptr);
                final byte[] bytes = new byte[length];
                ptr.readBytes(0, bytes, 0, length);
                final Rope rope = makeLeafRopeNode
                        .executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN, NotProvided.INSTANCE);
                return allocateObjectNode
                        .allocate(coreLibrary().getStringClass(), Layouts.STRING.build(false, true, rope));
            }
        }

    }

    @Primitive(name = "pointer_write_bytes", lowerFixnum = { 2, 3 })
    public static abstract class PointerWriteBytesNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "isRubyString(string)")
        protected DynamicObject writeBytes(long address, DynamicObject string, int index, int length,
                @Cached RopeNodes.BytesNode bytesNode) {
            final Pointer ptr = new Pointer(address);
            final Rope rope = StringOperations.rope(string);
            assert index + length <= rope.byteLength();
            if (length != 0) {
                // No need to check the pointer address if we write nothing
                checkNull(ptr);
            }

            ptr.writeBytes(0, bytesNode.execute(rope), index, length);
            return string;
        }

    }

    // Reads and writes of number types

    @Primitive(name = "pointer_read_char")
    public static abstract class PointerReadCharNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected int readCharSigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readByte(0);
        }

    }

    @Primitive(name = "pointer_read_uchar")
    public static abstract class PointerReadUCharNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected int readCharUnsigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return Byte.toUnsignedInt(ptr.readByte(0));
        }

    }

    @Primitive(name = "pointer_read_short")
    public static abstract class PointerReadShortNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected int readShortSigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readShort(0);
        }
    }

    @Primitive(name = "pointer_read_ushort")
    public static abstract class PointerReadUShortNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected int readShortUnsigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return Short.toUnsignedInt(ptr.readShort(0));
        }

    }

    @Primitive(name = "pointer_read_int")
    public static abstract class PointerReadIntNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected int readIntSigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readInt(0);
        }

    }

    @Primitive(name = "pointer_read_uint")
    public static abstract class PointerReadUIntNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected long readIntUnsigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return Integer.toUnsignedLong(ptr.readInt(0));
        }

    }

    @Primitive(name = "pointer_read_long")
    public static abstract class PointerReadLongNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected long readLongSigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readLong(0);
        }

    }

    @Primitive(name = "pointer_read_ulong")
    public static abstract class PointerReadULongNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected Object readLongUnsigned(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return readUnsignedLong(getContext(), ptr, 0);
        }

        @TruffleBoundary
        private static Object readUnsignedLong(RubyContext context, Pointer ptr, int offset) {
            long signedValue = ptr.readLong(offset);
            if (signedValue >= 0) {
                return signedValue;
            } else {
                return BignumOperations.createBignum(context, BigInteger.valueOf(signedValue).add(TWO_POW_64));
            }
        }

    }

    @Primitive(name = "pointer_read_float")
    public static abstract class PointerReadFloatNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected double readFloat(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readFloat(0);
        }

    }

    @Primitive(name = "pointer_read_double")
    public static abstract class PointerReadDoubleNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected double readDouble(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            return ptr.readDouble(0);
        }

    }

    @Primitive(name = "pointer_read_pointer")
    public static abstract class PointerReadPointerNode extends PointerPrimitiveArrayArgumentsNode {

        @Child private AllocateObjectNode allocateObjectNode = AllocateObjectNode.create();

        @Specialization
        protected DynamicObject readPointer(long address) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            final Pointer readPointer = ptr.readPointer(0);
            return allocateObjectNode.allocate(coreLibrary().getTruffleFFIPointerClass(), readPointer);
        }

    }

    @Primitive(name = "pointer_get_at_offset", lowerFixnum = { 1, 2 })
    @ImportStatic(NativeTypes.class)
    public static abstract class PointerGetAtOffsetPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "type == TYPE_CHAR")
        protected int getAtOffsetChar(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readByte(offset);
        }

        @Specialization(guards = "type == TYPE_UCHAR")
        protected int getAtOffsetUChar(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Byte.toUnsignedInt(ptr.readByte(offset));
        }

        @Specialization(guards = "type == TYPE_SHORT")
        protected int getAtOffsetShort(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readShort(offset);
        }

        @Specialization(guards = "type == TYPE_USHORT")
        protected int getAtOffsetUShort(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Short.toUnsignedInt(ptr.readShort(offset));
        }

        @Specialization(guards = "type == TYPE_INT")
        protected int getAtOffsetInt(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readInt(offset);
        }

        @Specialization(guards = "type == TYPE_UINT")
        protected long getAtOffsetUInt(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return Integer.toUnsignedLong(ptr.readInt(offset));
        }

        @Specialization(guards = "type == TYPE_LONG")
        protected long getAtOffsetLong(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readLong(offset);
        }

        @Specialization(guards = "type == TYPE_ULONG")
        protected Object getAtOffsetULong(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return PointerReadULongNode.readUnsignedLong(getContext(), ptr, offset);
        }

        @Specialization(guards = "type == TYPE_LL")
        protected long getAtOffsetLL(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return ptr.readLong(offset);
        }

        @Specialization(guards = "type == TYPE_ULL")
        protected Object getAtOffsetULL(DynamicObject pointer, int offset, int type) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            return PointerReadULongNode.readUnsignedLong(getContext(), ptr, offset);
        }

        @TruffleBoundary
        @Specialization(guards = "type == TYPE_STRING")
        protected DynamicObject getAtOffsetString(DynamicObject pointer, int offset, int type,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);

            final Pointer stringPointer = ptr.readPointer(offset);
            if (stringPointer.isNull()) {
                return nil();
            } else {
                final byte[] bytes = stringPointer.readZeroTerminatedByteArray(getContext(), 0);
                return makeStringNode.executeMake(bytes, ASCIIEncoding.INSTANCE, CodeRange.CR_UNKNOWN);
            }
        }

        @Specialization(guards = "type == TYPE_PTR")
        protected DynamicObject getAtOffsetPointer(DynamicObject pointer, int offset, int type,
                @Cached AllocateObjectNode allocateObjectNode) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final Pointer readPointer = ptr.readPointer(offset);
            return allocateObjectNode.allocate(Layouts.BASIC_OBJECT.getLogicalClass(pointer), readPointer);
        }

    }

    @Primitive(name = "pointer_set_at_offset", lowerFixnum = { 1, 2, 3 })
    @ImportStatic(NativeTypes.class)
    public static abstract class PointerSetAtOffsetPrimitiveNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "type == TYPE_CHAR")
        protected int setAtOffsetChar(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert ((byte) value) == value;
            ptr.writeByte(offset, (byte) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_UCHAR")
        protected int setAtOffsetUChar(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1 << Byte.SIZE);
            ptr.writeByte(offset, (byte) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_SHORT")
        protected int setAtOffsetShort(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert ((short) value) == value;
            ptr.writeShort(offset, (short) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_USHORT")
        protected int setAtOffsetUShort(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1 << Short.SIZE);
            ptr.writeShort(offset, (short) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_INT")
        protected int setAtOffsetInt(DynamicObject pointer, int offset, int type, int value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeInt(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_UINT")
        protected long setAtOffsetUInt(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0 && value < (1L << Integer.SIZE);
            ptr.writeInt(offset, (int) value);
            return value;
        }

        @Specialization(guards = "type == TYPE_LONG")
        protected long setAtOffsetLong(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_ULONG")
        protected long setAtOffsetULong(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            assert value >= 0L;
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_ULONG", "isRubyBignum(value)" })
        protected DynamicObject setAtOffsetULong(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            PointerWriteULongNode.writeUnsignedLong(ptr, offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_LL")
        protected long setAtOffsetLL(DynamicObject pointer, int offset, int type, long value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = "type == TYPE_ULL")
        protected long setAtOffsetULL(DynamicObject pointer, int offset, int type, long value) {
            assert value >= 0L;
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writeLong(offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_ULL", "isRubyBignum(value)" })
        protected DynamicObject setAtOffsetULL(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            PointerWriteULongNode.writeUnsignedLong(ptr, offset, value);
            return value;
        }

        @Specialization(guards = { "type == TYPE_PTR", "isRubyPointer(value)" })
        protected DynamicObject setAtOffsetPtr(DynamicObject pointer, int offset, int type, DynamicObject value) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            ptr.writePointer(offset, Layouts.POINTER.getPointer(value).getAddress());
            return value;
        }

        @TruffleBoundary
        @Specialization(guards = { "type == TYPE_CHARARR", "isRubyString(string)" })
        protected DynamicObject setAtOffsetCharArr(DynamicObject pointer, int offset, int type, DynamicObject string) {
            final Pointer ptr = Layouts.POINTER.getPointer(pointer);
            checkNull(ptr);
            final byte[] bytes = StringOperations.rope(string).getBytes();
            ptr.writeZeroTerminatedBytes(offset, bytes, 0, bytes.length);
            return string;
        }

    }

    @Primitive(name = "pointer_write_char", lowerFixnum = 1)
    @ImportStatic(Byte.class)
    public static abstract class PointerWriteCharNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = { "MIN_VALUE <= value", "value <= MAX_VALUE" })
        protected DynamicObject writeChar(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            byte byteValue = (byte) value;
            ptr.writeByte(0, byteValue);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_uchar", lowerFixnum = 1)
    @ImportStatic(Byte.class)
    public static abstract class PointerWriteUCharNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = { "0 <= value", "value <= MAX_VALUE" })
        protected DynamicObject writeChar(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            byte byteValue = (byte) value;
            ptr.writeByte(0, byteValue);
            return nil();
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < 256" })
        protected DynamicObject writeUnsignedChar(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            byte signed = (byte) value; // Same as value - 2^8
            ptr.writeByte(0, signed);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_short", lowerFixnum = 1)
    @ImportStatic(Short.class)
    public static abstract class PointerWriteShortNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = { "MIN_VALUE <= value", "value <= MAX_VALUE" })
        protected DynamicObject writeShort(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            short shortValue = (short) value;
            ptr.writeShort(0, shortValue);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_ushort", lowerFixnum = 1)
    @ImportStatic(Short.class)
    public static abstract class PointerWriteUShortNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = { "0 <= value", "value <= MAX_VALUE" })
        protected DynamicObject writeShort(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            short shortValue = (short) value;
            ptr.writeShort(0, shortValue);
            return nil();
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < 65536" })
        protected DynamicObject writeUnsignedSort(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            short signed = (short) value; // Same as value - 2^16
            ptr.writeShort(0, signed);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_int", lowerFixnum = 1)
    public static abstract class PointerWriteIntNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject writeInt(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeInt(0, value);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_uint", lowerFixnum = 1)
    @ImportStatic(Integer.class)
    public static abstract class PointerWriteUIntNode extends PointerPrimitiveArrayArgumentsNode {

        static final long MAX_UNSIGNED_INT_PLUS_ONE = 1L << Integer.SIZE;

        @Specialization(guards = "value >= 0")
        protected DynamicObject writeInt(long address, int value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeInt(0, value);
            return nil();
        }

        @Specialization(guards = { "value > MAX_VALUE", "value < MAX_UNSIGNED_INT_PLUS_ONE" })
        protected DynamicObject writeUnsignedInt(long address, long value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            int signed = (int) value; // Same as value - 2^32
            ptr.writeInt(0, signed);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_long")
    public static abstract class PointerWriteLongNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject writeLong(long address, long value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeLong(0, value);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_ulong")
    public static abstract class PointerWriteULongNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization(guards = "value >= 0")
        protected DynamicObject writeLong(long address, long value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeLong(0, value);
            return nil();
        }

        @Specialization(guards = "isRubyBignum(value)")
        protected DynamicObject writeUnsignedLong(long address, DynamicObject value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            writeUnsignedLong(ptr, 0, value);
            return nil();
        }

        @TruffleBoundary
        private static void writeUnsignedLong(Pointer ptr, int offset, DynamicObject value) {
            BigInteger v = Layouts.BIGNUM.getValue(value);
            assert v.signum() >= 0;
            assert v.compareTo(TWO_POW_64) < 0;
            BigInteger signed = v.subtract(TWO_POW_64);
            ptr.writeLong(offset, signed.longValueExact());
        }

    }

    @Primitive(name = "pointer_write_float")
    public static abstract class PointerWriteFloatNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject writeFloat(long address, double value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeFloat(0, (float) value);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_double")
    public static abstract class PointerWriteDoubleNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject writeDouble(long address, double value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writeDouble(0, value);
            return nil();
        }

    }

    @Primitive(name = "pointer_write_pointer", lowerFixnum = 2)
    public static abstract class PointerWritePointerNode extends PointerPrimitiveArrayArgumentsNode {

        @Specialization
        protected DynamicObject writePointer(long address, long value) {
            final Pointer ptr = new Pointer(address);
            checkNull(ptr);
            ptr.writePointer(0, value);
            return nil();
        }

    }

    @Primitive(name = "pointer_raw_malloc")
    public static abstract class PointerRawMallocPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long realloc(long size) {
            return Pointer.rawMalloc(size);
        }

    }

    @Primitive(name = "pointer_raw_realloc")
    public static abstract class PointerRawReallocPrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long malloc(long address, long size) {
            return Pointer.rawRealloc(address, size);
        }

    }

    @Primitive(name = "pointer_raw_free")
    public static abstract class PointerRawFreePrimitiveNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected long free(long address) {
            Pointer.rawFree(address);
            return address;
        }

    }

}
