package com.example.myapplication.ui.screen.chat

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val messagePreviewExtensions = listOf(
    TablesExtension.create(),
    AutolinkExtension.create(),
)

private val messagePreviewParser: Parser = Parser.builder()
    .extensions(messagePreviewExtensions)
    .build()

private val messagePreviewRenderer: HtmlRenderer = HtmlRenderer.builder()
    .extensions(messagePreviewExtensions)
    .escapeHtml(true)
    .sanitizeUrls(true)
    .build()

fun buildMessagePreviewHtml(
    title: String,
    markdown: String,
    colorScheme: ColorScheme,
): String {
    val document: Node = messagePreviewParser.parse(markdown)
    val renderedHtml = messagePreviewRenderer.render(document).ifBlank {
        "<p>暂无可预览内容</p>"
    }

    return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover" />
          <title>${title.escapeHtml()}</title>
          <style>
            :root {
              --bg: ${colorScheme.background.toCssHex()};
              --surface: ${colorScheme.surface.toCssHex()};
              --surface-variant: ${colorScheme.surfaceVariant.toCssHex()};
              --text: ${colorScheme.onBackground.toCssHex()};
              --muted: ${colorScheme.onSurfaceVariant.toCssHex()};
              --primary: ${colorScheme.primary.toCssHex()};
              --outline: ${colorScheme.outline.toCssHex()};
            }
            * { box-sizing: border-box; }
            html, body {
              margin: 0;
              padding: 0;
              background: linear-gradient(180deg, var(--bg) 0%, var(--surface) 100%);
              color: var(--text);
              font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
              line-height: 1.72;
            }
            body {
              padding: 20px 16px 32px;
            }
            .frame {
              max-width: 920px;
              margin: 0 auto;
              background: color-mix(in srgb, var(--surface) 88%, white 12%);
              border: 1px solid color-mix(in srgb, var(--outline) 28%, transparent 72%);
              border-radius: 20px;
              box-shadow: 0 18px 42px rgba(0, 0, 0, 0.08);
              overflow: hidden;
            }
            .hero {
              padding: 18px 20px 10px;
              background:
                radial-gradient(circle at top right, color-mix(in srgb, var(--primary) 16%, transparent 84%), transparent 42%),
                linear-gradient(180deg, color-mix(in srgb, var(--surface-variant) 36%, transparent 64%), transparent 100%);
              border-bottom: 1px solid color-mix(in srgb, var(--outline) 18%, transparent 82%);
            }
            .hero h1 {
              margin: 0;
              font-size: 20px;
              line-height: 1.4;
            }
            .hero p {
              margin: 8px 0 0;
              font-size: 13px;
              color: var(--muted);
            }
            article {
              padding: 18px 20px 24px;
            }
            h1, h2, h3, h4, h5, h6 {
              line-height: 1.35;
              margin: 1.2em 0 0.55em;
            }
            p, ul, ol, blockquote, table, pre {
              margin: 0 0 1em;
            }
            a {
              color: var(--primary);
              text-decoration: none;
            }
            a:hover {
              text-decoration: underline;
            }
            pre {
              overflow-x: auto;
              padding: 14px 16px;
              border-radius: 16px;
              background: color-mix(in srgb, var(--surface-variant) 72%, transparent 28%);
              border: 1px solid color-mix(in srgb, var(--outline) 16%, transparent 84%);
            }
            code {
              font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
              font-size: 0.92em;
            }
            :not(pre) > code {
              padding: 0.15em 0.45em;
              border-radius: 8px;
              background: color-mix(in srgb, var(--surface-variant) 58%, transparent 42%);
            }
            table {
              width: 100%;
              border-collapse: collapse;
              overflow: hidden;
              border-radius: 16px;
              border: 1px solid color-mix(in srgb, var(--outline) 20%, transparent 80%);
            }
            th, td {
              padding: 10px 12px;
              border-bottom: 1px solid color-mix(in srgb, var(--outline) 14%, transparent 86%);
              text-align: left;
              vertical-align: top;
            }
            th {
              background: color-mix(in srgb, var(--surface-variant) 46%, transparent 54%);
            }
            blockquote {
              padding: 10px 14px;
              border-left: 4px solid color-mix(in srgb, var(--primary) 60%, white 40%);
              background: color-mix(in srgb, var(--surface-variant) 38%, transparent 62%);
              border-radius: 0 14px 14px 0;
              color: color-mix(in srgb, var(--text) 86%, var(--muted) 14%);
            }
            img {
              max-width: 100%;
              height: auto;
              border-radius: 16px;
            }
            hr {
              border: none;
              border-top: 1px solid color-mix(in srgb, var(--outline) 18%, transparent 82%);
              margin: 1.5em 0;
            }
          </style>
        </head>
        <body>
          <div class="frame">
            <header class="hero">
              <h1>${title.escapeHtml()}</h1>
              <p>消息渲染预览</p>
            </header>
            <article>$renderedHtml</article>
          </div>
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String {
    return buildString(length) {
        for (char in this@escapeHtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toCssHex(): String {
    return "#%08X".format(toArgb())
}
