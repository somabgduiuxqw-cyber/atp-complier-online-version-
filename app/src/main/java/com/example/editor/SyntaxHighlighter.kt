package com.example.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.CodeAttribute
import com.example.ui.theme.CodeComment
import com.example.ui.theme.CodeKeyword
import com.example.ui.theme.CodeNumber
import com.example.ui.theme.CodeString
import com.example.ui.theme.CodeTag
import com.example.ui.theme.CodeType
import java.util.regex.Pattern

object SyntaxHighlighter {
    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "if", "else", "when", "for", "while", "return", "override", "public",
        "private", "protected", "internal", "data", "sealed", "open", "abstract",
        "companion", "suspend", "lateinit", "lazy", "null", "true", "false",
        "this", "super", "is", "in", "by", "as", "try", "catch", "finally", "throw"
    )

    private val JAVA_KEYWORDS = setOf(
        "package", "import", "public", "private", "protected", "class", "interface",
        "extends", "implements", "static", "final", "void", "int", "boolean", "float",
        "double", "long", "byte", "char", "short", "if", "else", "for", "while",
        "return", "new", "this", "super", "try", "catch", "finally", "throw", "throws",
        "null", "true", "false"
    )

    private val GRADLE_KEYWORDS = setOf(
        "plugins", "id", "alias", "apply", "version", "android", "namespace",
        "compileSdk", "defaultConfig", "applicationId", "minSdk", "targetSdk",
        "versionCode", "versionName", "buildTypes", "release", "debug",
        "dependencies", "implementation", "testImplementation", "androidTestImplementation"
    )

    fun highlight(text: String, fileName: String): AnnotatedString {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> highlightKotlin(text)
            "java" -> highlightJava(text)
            "xml" -> highlightXml(text)
            "gradle" -> highlightKotlin(text)
            "json" -> highlightJson(text)
            else -> buildAnnotatedString { append(text) }
        }
    }

    private fun highlightKotlin(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            // Keywords
            val wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")
            val wordMatcher = wordPattern.matcher(text)
            while (wordMatcher.find()) {
                val word = wordMatcher.group(1) ?: continue
                if (KOTLIN_KEYWORDS.contains(word) || GRADLE_KEYWORDS.contains(word)) {
                    addStyle(
                        SpanStyle(color = CodeKeyword, fontWeight = FontWeight.Bold),
                        wordMatcher.start(), wordMatcher.end()
                    )
                } else if (word.first().isUpperCase()) {
                    addStyle(
                        SpanStyle(color = CodeType, fontWeight = FontWeight.Medium),
                        wordMatcher.start(), wordMatcher.end()
                    )
                }
            }

            // String literals: "..."
            highlightPattern(this, text, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), CodeString)

            // Numbers
            highlightPattern(this, text, Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:f|L|u)?\\b"), CodeNumber)

            // Comments: // ... or /* ... */
            highlightPattern(this, text, Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"), CodeComment)
        }
    }

    private fun highlightJava(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            val wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")
            val wordMatcher = wordPattern.matcher(text)
            while (wordMatcher.find()) {
                val word = wordMatcher.group(1) ?: continue
                if (JAVA_KEYWORDS.contains(word)) {
                    addStyle(
                        SpanStyle(color = CodeKeyword, fontWeight = FontWeight.Bold),
                        wordMatcher.start(), wordMatcher.end()
                    )
                } else if (word.first().isUpperCase()) {
                    addStyle(
                        SpanStyle(color = CodeType, fontWeight = FontWeight.Medium),
                        wordMatcher.start(), wordMatcher.end()
                    )
                }
            }
            highlightPattern(this, text, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), CodeString)
            highlightPattern(this, text, Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:f|L)?\\b"), CodeNumber)
            highlightPattern(this, text, Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/"), CodeComment)
        }
    }

    private fun highlightXml(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            // Tags: <tag or </tag
            highlightPattern(this, text, Pattern.compile("</?[a-zA-Z0-9_:-]+"), CodeTag)
            // Attributes: android:name=
            highlightPattern(this, text, Pattern.compile("[a-zA-Z0-9_:-]+(?=\\s*=)"), CodeAttribute)
            // Strings: "..."
            highlightPattern(this, text, Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\""), CodeString)
            // Comments: <!-- ... -->
            highlightPattern(this, text, Pattern.compile("<!--[\\s\\S]*?-->"), CodeComment)
        }
    }

    private fun highlightJson(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            // Keys
            highlightPattern(this, text, Pattern.compile("\"[^\"]*\"(?=\\s*:)"), CodeAttribute)
            // String values
            highlightPattern(this, text, Pattern.compile(":\\s*\"[^\"]*\""), CodeString)
            // Numbers
            highlightPattern(this, text, Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"), CodeNumber)
            // Booleans & null
            highlightPattern(this, text, Pattern.compile("\\b(true|false|null)\\b"), CodeKeyword)
        }
    }

    private fun highlightPattern(
        builder: AnnotatedString.Builder,
        text: String,
        pattern: Pattern,
        color: Color
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            builder.addStyle(
                SpanStyle(color = color),
                matcher.start(), matcher.end()
            )
        }
    }
}
