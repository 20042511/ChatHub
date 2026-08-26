package me.rerere.readstack

import me.rerere.readstack.data.source.docs.htmlToMarkdown
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownTest {

    @Test
    fun headings_becomeMarkdownAtx() {
        val html = "<html><body><h1>Title</h1><h2>Sub</h2><p>Body text.</p></body></html>"
        val md = htmlToMarkdown(Jsoup.parse(html))
        assertTrue(md.contains("# Title"))
        assertTrue(md.contains("## Sub"))
        assertTrue(md.contains("Body text."))
    }

    @Test
    fun links_becomeMarkdownLinks() {
        val html = """<html><body><p>See <a href="https://example.com">example</a>.</p></body></html>"""
        val md = htmlToMarkdown(Jsoup.parse(html))
        assertTrue(md.contains("[example](https://example.com)"))
    }

    @Test
    fun scriptAndStyle_areStripped() {
        val html = """<html><body><script>alert('x')</script><style>body{}</style><p>kept</p></body></html>"""
        val md = htmlToMarkdown(Jsoup.parse(html))
        assertTrue(md.contains("kept"))
        assertTrue(!md.contains("alert"))
    }
}
