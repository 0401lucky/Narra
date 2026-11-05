package com.example.myapplication.ui.theme

/**
 * 试点源码的词法守卫：只识别已登记的构造 / 布局调用，不解析 Kotlin 类型或追踪变量。
 * 注释、字符与字符串先等长屏蔽，使报错行号和声明仍对应真实源码。
 */
internal object UiTokenSourceGuard {
    enum class Rule { SHAPE, COLOR, SPACING }

    data class Scope(
        val path: String,
        val requiredDeclarations: Set<String> = emptySet(),
        val scannedDeclarations: Set<String>? = null,
        val legacyDeclarations: Set<String> = emptySet(),
        val expectedDeclarationCounts: Map<String, Int> = emptyMap(),
    )

    data class Exception(
        val path: String,
        val declaration: String,
        val rule: Rule,
        val expression: String,
        val reason: String,
        val occurrences: Int = 1,
    ) {
        val key get() = Key(path, declaration, rule, canonical(expression))
    }

    data class Key(val path: String, val declaration: String, val rule: Rule, val expression: String)

    data class Finding(val key: Key, val line: Int) {
        override fun toString() = "${key.path}:$line [${key.declaration}] ${key.rule}: ${key.expression}"
    }

    fun unregisteredPilotFiles(paths: Set<String>, scopes: List<Scope>): Set<String> {
        val registered = scopes.map { it.path }.toSet()
        val stems = registered.map { it.removeSuffix(".kt") }
        return paths.filter { path -> path.endsWith(".kt") && stems.any(path::startsWith) }.toSet() - registered
    }

    fun audit(
        sources: Map<String, String>,
        scopes: List<Scope>,
        exceptions: List<Exception> = emptyList(),
    ): List<String> = buildList {
        val paths = scopes.map { it.path }
        if (paths.size != paths.toSet().size) add("重复的扫描路径")
        (paths.toSet() - sources.keys).forEach { add("缺少扫描文件：$it") }
        (sources.keys - paths.toSet()).forEach { add("未登记扫描文件：$it") }

        val findings = scopes.flatMap { scope ->
            val source = sources[scope.path] ?: return@flatMap emptyList()
            val declarations = declarations(maskNonCode(source)).map { it.second }
            val checkedDeclarations = scope.requiredDeclarations + scope.scannedDeclarations.orEmpty()
            scope.expectedDeclarationCounts.forEach { (name, count) ->
                if (name !in checkedDeclarations || count <= 0) {
                    add("无效的声明数量登记：${scope.path}#$name=$count")
                }
            }
            fun checkDeclarationCount(name: String, label: String) {
                val expected = scope.expectedDeclarationCounts[name] ?: 1
                if (declarations.count { it == name } != expected) {
                    val requirement = if (expected == 1) "必须存在且唯一" else "数量必须为 $expected"
                    add("$label$requirement：${scope.path}#$name")
                }
            }
            scope.requiredDeclarations.forEach { name ->
                checkDeclarationCount(name, "声明")
            }
            scope.scannedDeclarations?.let { scanned ->
                scanned.forEach { name ->
                    checkDeclarationCount(name, "扫描声明")
                }
                val registered = scanned + scope.legacyDeclarations
                (declarations.toSet() - registered).forEach { add("未登记声明：${scope.path}#$it") }
                (registered - declarations.toSet()).forEach { add("陈旧的声明范围：${scope.path}#$it") }
                if ((scanned intersect scope.legacyDeclarations).isNotEmpty()) add("扫描 / 旧声明范围重叠：${scope.path}")
            }
            scan(scope.path, source).filter { scope.scannedDeclarations == null || it.key.declaration in scope.scannedDeclarations }
        }

        val actual = findings.groupBy { it.key }
        val approved = exceptions.groupBy { it.key }
        approved.forEach { (key, entries) ->
            if (entries.size != 1) add("重复例外：$key")
            val exception = entries.first()
            if (exception.reason.isBlank() || exception.occurrences <= 0) add("无效例外：$key")
            val count = actual[key].orEmpty().size
            if (count != exception.occurrences) {
                add("例外未精确消费（实际 $count，登记 ${exception.occurrences}）：$key；${exception.reason}")
            }
        }
        findings.filter { it.key !in approved }.forEach { add("新增硬编码：$it") }
    }

    fun scan(path: String, source: String): List<Finding> {
        val code = maskNonCode(source)
        val declarations = declarations(code)
        fun finding(rule: Rule, start: Int, expression: String): Finding {
            val declaration = declarations.lastOrNull { it.first <= start }?.second ?: "<file>"
            return Finding(Key(path, declaration, rule, canonical(expression)), source.take(start).count { it == '\n' } + 1)
        }
        val calls = Calls.findAll(code).mapNotNull { match ->
            val start = match.range.first
            val end = closingParenthesis(code, match.range.last) ?: return@mapNotNull null
            val expression = code.substring(start, end + 1)
            val name = match.groupValues[1]
            val rule = when {
                name == "Color" && isFixedColorCall(expression) -> Rule.COLOR
                name == "RoundedCornerShape" && (DpLiteral.containsMatchIn(expression) || CornerNumber.containsMatchIn(expression)) -> Rule.SHAPE
                name in LayoutCalls && DpLiteral.findAll(expression).any { it.value.isNonZeroDp() } -> Rule.SPACING
                else -> return@mapNotNull null
            }
            finding(rule, start, expression)
        }.toList()
        val namedColors = NamedColor.findAll(code).map { finding(Rule.COLOR, it.range.first, it.value) }
        return (calls + namedColors).sortedBy { it.line }
    }

    private fun isFixedColorCall(expression: String): Boolean {
        val arguments = expression.substringAfter('(').dropLast(1).split(',').map(String::trim).filter(String::isNotEmpty)
        val named = arguments.filter { '=' in it }.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        if (named.isNotEmpty()) {
            val rgb = listOf("red", "green", "blue")
            if (named.keys.containsAll(rgb)) return rgb.all { NumberLiteral.matches(named.getValue(it)) }
            return named["color"]?.let(NumberLiteral::matches) == true
        }
        // 用户保存的 ARGB / RGB 变量合法；固定 RGB 不因独立 alpha 变量而绕过检查。
        return if (arguments.size == 1) NumberLiteral.matches(arguments.single())
        else arguments.size >= 3 && arguments.take(3).all(NumberLiteral::matches)
    }

    private fun String.isNonZeroDp(): Boolean = replace(Regex("\\s"), "")
        .removeSuffix(".dp")
        .removeSuffix("f")
        .removeSuffix("F")
        .replace("_", "")
        .toDoubleOrNull() != 0.0

    private fun declarations(code: String): List<Pair<Int, String>> = Declarations.findAll(code).map {
        it.range.first to (it.groups[1]?.value ?: it.groupValues[2])
    }.toList()

    private fun closingParenthesis(code: String, open: Int): Int? {
        var depth = 0
        for (index in open until code.length) {
            when (code[index]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return index
            }
        }
        return null
    }

    private fun canonical(expression: String): String = maskNonCode(expression).replace(Regex("\\s+"), "")

    private fun maskNonCode(source: String): String {
        val result = source.toCharArray()
        var index = 0
        fun blankTo(end: Int) {
            while (index < end) {
                if (result[index] != '\n' && result[index] != '\r') result[index] = ' '
                index++
            }
        }
        while (index < source.length) {
            when {
                source.startsWith("//", index) -> blankTo(source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length)
                source.startsWith("/*", index) -> {
                    var end = index + 2
                    var depth = 1
                    while (end < source.length && depth > 0) {
                        when {
                            source.startsWith("/*", end) -> { depth++; end += 2 }
                            source.startsWith("*/", end) -> { depth--; end += 2 }
                            else -> end++
                        }
                    }
                    blankTo(end)
                }
                source.startsWith("\"\"\"", index) -> {
                    val end = source.indexOf("\"\"\"", index + 3)
                    blankTo(if (end >= 0) end + 3 else source.length)
                }
                source[index] == '"' || source[index] == '\'' -> {
                    val quote = source[index]
                    var end = index + 1
                    while (end < source.length) {
                        if (source[end] == '\\') end = (end + 2).coerceAtMost(source.length)
                        else if (source[end++] == quote) break
                    }
                    blankTo(end)
                }
                else -> index++
            }
        }
        return String(result)
    }

    private val Calls = Regex("\\b(Color|RoundedCornerShape|padding|absolutePadding|PaddingValues|spacedBy|Spacer)\\s*\\(")
    private val LayoutCalls = setOf("padding", "absolutePadding", "PaddingValues", "spacedBy", "Spacer")
    private val NumberLiteral = Regex("[+-]?(?:0[xX][0-9a-fA-F_]+(?:[uU][lL]?|[lL])?|(?:\\d[\\d_]*(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fFdDlLuU]*)")
    private val NamedColor = Regex("\\bColor\\s*\\.\\s*(?:Black|White|Transparent|Unspecified|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\\b")
    private val DpLiteral = Regex("(?<![\\w.])[-+]?\\d[\\d_]*(?:\\.\\d[\\d_]*)?[fF]?\\s*\\.\\s*dp\\b")
    private val CornerNumber = Regex("[(=,]\\s*\\d+(?:\\.\\d+)?[fF]?\\s*(?=[,)])")
    private val Declarations = Regex(
        "(?m)^(?:(?:private|internal|public|protected|data|enum|sealed|inline|suspend|const|lateinit|override|tailrec|operator|infix|external|actual|expect|open|abstract)\\s+)*" +
            "(?:fun\\s+(?:<[^>\\n]+>\\s+)?([A-Za-z_][\\w.]*)\\s*\\(|(?:val|var|object|class|interface)\\s+([A-Za-z_]\\w*))",
    )
}
