package org.ukky.notitrace.util

import java.security.MessageDigest

/**
 * 通知相関用 SHA-256 ハッシュ生成器。
 *
 * packageName + title + text + bigText + subText を連結してハッシュ化する。
 * null フィールドには区別用のマーカーを使い、null と空文字列を区別する。
 */
object SignatureGenerator {

    private const val NULL_MARKER = "\u0000NULL\u0000"
    private const val SEPARATOR = "\u001F" // Unit Separator

    fun generate(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        subText: String?,
    ): String {
        val input = listOf(
            packageName,
            title ?: NULL_MARKER,
            text ?: NULL_MARKER,
            bigText ?: NULL_MARKER,
            subText ?: NULL_MARKER,
        ).joinToString(SEPARATOR)

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
