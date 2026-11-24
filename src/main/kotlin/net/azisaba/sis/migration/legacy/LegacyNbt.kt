package net.azisaba.sis.migration.legacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A minimal NBT reader and writer (gzip, big endian) for the base64 "internal" NBT that 1.15.2 Bukkit kept in item
 * meta, and for whole items ([org.bukkit.inventory.ItemStack.serializeAsBytes]).
 *
 * Compounds are `Map<String, Any?>`, lists are [NbtList], arrays are `ByteArray` / `IntArray` / `LongArray`.
 */
object LegacyNbt {
    /** A list tag that remembers its element type, so it can be written back (even when empty). */
    class NbtList(val elementType: Byte) : ArrayList<Any?>()

    fun read(data: ByteArray): MutableMap<String, Any?> =
        DataInputStream(GZIPInputStream(ByteArrayInputStream(data))).use { input ->
            if (input.readByte() != TAG_COMPOUND) throw IOException("root is not a compound")
            input.readUTF()
            readCompound(input)
        }

    /** The NBT of a base64 "internal" string (whitespace is ignored), or null when it cannot be read. */
    fun readInternal(internal: String): MutableMap<String, Any?>? =
        runCatching { read(Base64.getDecoder().decode(internal.replace(Regex("\\s"), ""))) }.getOrNull()

    fun write(root: Map<String, Any?>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(bytes)).use { out ->
            out.writeByte(TAG_COMPOUND.toInt())
            out.writeUTF("")
            writeCompound(out, root)
        }
        return bytes.toByteArray()
    }

    fun writeInternal(root: Map<String, Any?>): String = Base64.getEncoder().encodeToString(write(root))

    private const val TAG_COMPOUND: Byte = 10

    private fun readCompound(input: DataInputStream): MutableMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        while (true) {
            val type = input.readByte()
            if (type.toInt() == 0) return result
            val name = input.readUTF()
            result[name] = readTag(input, type)
        }
    }

    private fun readTag(input: DataInputStream, type: Byte): Any? = when (type.toInt()) {
        1 -> input.readByte()
        2 -> input.readShort()
        3 -> input.readInt()
        4 -> input.readLong()
        5 -> input.readFloat()
        6 -> input.readDouble()
        7 -> ByteArray(input.readInt()).also { input.readFully(it) }
        8 -> input.readUTF()
        9 -> {
            val elementType = input.readByte()
            val size = input.readInt()
            NbtList(elementType).apply { repeat(size) { add(readTag(input, elementType)) } }
        }
        10 -> readCompound(input)
        11 -> IntArray(input.readInt()) { input.readInt() }
        12 -> LongArray(input.readInt()) { input.readLong() }
        else -> throw IOException("unknown tag type $type")
    }

    private fun writeCompound(out: DataOutputStream, compound: Map<*, *>) {
        for ((key, value) in compound) {
            out.writeByte(typeOf(value).toInt())
            out.writeUTF(key.toString())
            writeTag(out, value)
        }
        out.writeByte(0)
    }

    private fun typeOf(value: Any?): Byte = when (value) {
        is Byte -> 1
        is Short -> 2
        is Int -> 3
        is Long -> 4
        is Float -> 5
        is Double -> 6
        is ByteArray -> 7
        is String -> 8
        is List<*> -> 9
        is Map<*, *> -> 10
        is IntArray -> 11
        is LongArray -> 12
        else -> throw IOException("unsupported tag value $value")
    }

    private fun writeTag(out: DataOutputStream, value: Any?) {
        when (value) {
            is Byte -> out.writeByte(value.toInt())
            is Short -> out.writeShort(value.toInt())
            is Int -> out.writeInt(value)
            is Long -> out.writeLong(value)
            is Float -> out.writeFloat(value)
            is Double -> out.writeDouble(value)
            is ByteArray -> {
                out.writeInt(value.size)
                out.write(value)
            }
            is String -> out.writeUTF(value)
            is List<*> -> {
                val elementType = (value as? NbtList)?.elementType ?: if (value.isEmpty()) 0 else typeOf(value[0])
                out.writeByte(elementType.toInt())
                out.writeInt(value.size)
                value.forEach { writeTag(out, it) }
            }
            is Map<*, *> -> writeCompound(out, value)
            is IntArray -> {
                out.writeInt(value.size)
                value.forEach(out::writeInt)
            }
            is LongArray -> {
                out.writeInt(value.size)
                value.forEach(out::writeLong)
            }
            else -> throw IOException("unsupported tag value $value")
        }
    }
}
