package com.example.myapplication.roleplay

/** 在已分离标记的文本上单次解码，编码的尖括号不会再作为标记解析。 */
internal object RoleplayXmlEntities {
    private val pattern = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|amp|lt|gt|quot|apos);")

    fun decodeOnce(value: String): String = pattern.replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val codePoint =
                    if (entity.startsWith("#x", ignoreCase = true)) entity.drop(2).toIntOrNull(16)
                    else entity.drop(1).toIntOrNull()
                codePoint?.takeIf {
                    it == 9 || it == 10 || it == 13 || it in 0x20..0xD7FF || it in 0xE000..0xFFFD || it in 0x10000..0x10FFFF
                }?.let { String(Character.toChars(it)) } ?: match.value
            }
        }
    }
}
