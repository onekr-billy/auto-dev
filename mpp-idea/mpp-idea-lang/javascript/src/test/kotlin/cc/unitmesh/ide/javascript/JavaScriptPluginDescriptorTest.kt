package cc.unitmesh.ide.javascript

import org.junit.Test
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertTrue

class JavaScriptPluginDescriptorTest {
    @Test
    fun testDescriptorDeclaresRequiredJavaScriptV2Modules() {
        val dependencies = parseDependencies("/cc.unitmesh.javascript.xml")

        assertTrue("intellij.javascript.backend" in dependencies, "Missing required module: intellij.javascript.backend")
        assertTrue("intellij.javascript.common" in dependencies, "Missing required module: intellij.javascript.common")
        assertTrue("intellij.javascript.psi.impl" in dependencies, "Missing required module: intellij.javascript.psi.impl")
        assertTrue("intellij.javascript.testing" in dependencies, "Missing required module: intellij.javascript.testing")
    }

    private fun parseDependencies(resourcePath: String): Set<String> {
        val stream = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Missing resource: $resourcePath"
        }

        stream.use { input ->
            val document = documentBuilderFactory().newDocumentBuilder().parse(input)
            return document.getElementsByTagName("module")
                .let { nodes -> (0 until nodes.length).map { index -> nodes.item(index) } }
                .mapNotNull { it.attributes?.getNamedItem("name")?.nodeValue }
                .toSet()
        }
    }

    private fun documentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setExpandEntityReferences(false)
        }
    }
}
