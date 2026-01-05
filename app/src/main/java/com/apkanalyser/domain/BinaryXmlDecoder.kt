package com.apkanalyser.domain

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes Android binary XML (AXML) to readable XML text.
 * Used for decoding AndroidManifest.xml from APK files.
 */
class BinaryXmlDecoder {
    
    companion object {
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_START_NAMESPACE = 0x00100100
        private const val CHUNK_END_NAMESPACE = 0x00100101
        private const val CHUNK_START_TAG = 0x00100102
        private const val CHUNK_END_TAG = 0x00100103
        private const val CHUNK_TEXT = 0x00100104
        
        private const val TYPE_NULL = 0x00
        private const val TYPE_REFERENCE = 0x01
        private const val TYPE_STRING = 0x03
        private const val TYPE_INT_DEC = 0x10
        private const val TYPE_INT_HEX = 0x11
        private const val TYPE_INT_BOOLEAN = 0x12
    }
    
    fun decode(input: InputStream): String {
        val bytes = input.readBytes()
        return decode(bytes)
    }
    
    fun decode(data: ByteArray): String {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        // Verify AXML header
        val magic = buffer.int
        if (magic != CHUNK_AXML_FILE) {
            return "<!-- Unable to decode: not a valid Android binary XML file -->"
        }
        
        buffer.int // file size
        
        val stringPool = mutableListOf<String>()
        val namespaces = mutableMapOf<String, String>()
        val output = StringBuilder()
        output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        
        var indentLevel = 0
        
        try {
            while (buffer.hasRemaining()) {
                val chunkStart = buffer.position()
                val chunkType = buffer.int
                val chunkSize = buffer.int
                
                when (chunkType) {
                    CHUNK_STRING_POOL -> {
                        parseStringPool(buffer, chunkStart, chunkSize, stringPool)
                    }
                    CHUNK_RESOURCE_IDS -> {
                        // Skip resource IDs
                        buffer.position(chunkStart + chunkSize)
                    }
                    CHUNK_START_NAMESPACE -> {
                        buffer.int // line number
                        buffer.int // comment
                        val prefix = buffer.int
                        val uri = buffer.int
                        if (prefix >= 0 && prefix < stringPool.size && 
                            uri >= 0 && uri < stringPool.size) {
                            namespaces[stringPool[uri]] = stringPool[prefix]
                        }
                    }
                    CHUNK_END_NAMESPACE -> {
                        buffer.position(chunkStart + chunkSize)
                    }
                    CHUNK_START_TAG -> {
                        val indent = "    ".repeat(indentLevel)
                        buffer.int // line number
                        buffer.int // comment
                        val namespaceUri = buffer.int
                        val name = buffer.int
                        buffer.short // attribute start
                        val attrSize = buffer.short.toInt()
                        val attrCount = buffer.short.toInt()
                        buffer.short // id index
                        buffer.short // class index
                        buffer.short // style index
                        
                        val tagName = if (name >= 0 && name < stringPool.size) stringPool[name] else "unknown"
                        output.append("$indent<$tagName")
                        
                        // Add namespace declarations at root
                        if (indentLevel == 0) {
                            namespaces.forEach { (uri, prefix) ->
                                output.append("\n$indent    xmlns:$prefix=\"$uri\"")
                            }
                        }
                        
                        // Parse attributes
                        for (i in 0 until attrCount) {
                            val attrNs = buffer.int
                            val attrName = buffer.int
                            val attrValueStr = buffer.int
                            val attrType = buffer.int
                            val attrValue = buffer.int
                            
                            val attrNameStr = if (attrName >= 0 && attrName < stringPool.size) 
                                stringPool[attrName] else "attr$i"
                            
                            val prefix = if (attrNs >= 0 && attrNs < stringPool.size) {
                                namespaces[stringPool[attrNs]]?.let { "$it:" } ?: ""
                            } else ""
                            
                            val valueStr = when (attrType shr 24) {
                                TYPE_STRING -> if (attrValueStr >= 0 && attrValueStr < stringPool.size) 
                                    stringPool[attrValueStr] else "?"
                                TYPE_INT_BOOLEAN -> if (attrValue != 0) "true" else "false"
                                TYPE_INT_HEX -> "0x${attrValue.toString(16)}"
                                TYPE_REFERENCE -> "@0x${attrValue.toString(16)}"
                                TYPE_INT_DEC -> attrValue.toString()
                                else -> attrValue.toString()
                            }
                            
                            output.append("\n$indent    $prefix$attrNameStr=\"$valueStr\"")
                        }
                        
                        output.append(">\n")
                        indentLevel++
                    }
                    CHUNK_END_TAG -> {
                        indentLevel--
                        val indent = "    ".repeat(indentLevel)
                        buffer.int // line number
                        buffer.int // comment
                        buffer.int // namespace
                        val name = buffer.int
                        
                        val tagName = if (name >= 0 && name < stringPool.size) stringPool[name] else "unknown"
                        output.append("$indent</$tagName>\n")
                    }
                    CHUNK_TEXT -> {
                        buffer.position(chunkStart + chunkSize)
                    }
                    else -> {
                        buffer.position(chunkStart + chunkSize)
                    }
                }
            }
        } catch (e: Exception) {
            output.append("\n<!-- Decode error: ${e.message} -->")
        }
        
        return output.toString()
    }
    
    private fun parseStringPool(
        buffer: ByteBuffer,
        chunkStart: Int,
        chunkSize: Int,
        stringPool: MutableList<String>
    ) {
        val stringCount = buffer.int
        buffer.int // style count
        val flags = buffer.int
        val stringsStart = buffer.int
        buffer.int // styles start
        
        val isUtf8 = (flags and 0x100) != 0
        
        val offsets = IntArray(stringCount) { buffer.int }
        
        val stringsOffset = chunkStart + 8 + stringsStart
        
        for (i in 0 until stringCount) {
            buffer.position(stringsOffset + offsets[i])
            val str = if (isUtf8) {
                readUtf8String(buffer)
            } else {
                readUtf16String(buffer)
            }
            stringPool.add(str)
        }
        
        buffer.position(chunkStart + chunkSize)
    }
    
    private fun readUtf8String(buffer: ByteBuffer): String {
        // Skip length bytes
        var len = buffer.get().toInt() and 0xFF
        if (len and 0x80 != 0) {
            len = ((len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        // Skip encoded length
        var encodedLen = buffer.get().toInt() and 0xFF
        if (encodedLen and 0x80 != 0) {
            encodedLen = ((encodedLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        
        val bytes = ByteArray(encodedLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
    
    private fun readUtf16String(buffer: ByteBuffer): String {
        var len = buffer.short.toInt() and 0xFFFF
        if (len and 0x8000 != 0) {
            len = ((len and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
        }
        
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = buffer.short.toInt().toChar()
        }
        return String(chars)
    }
}
