package dev.ajithgoveas.kindex.parser.extractors

import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.SymbolType
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractorGoldenTest {

    private val snippetDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kindex-golden"

    private fun snippet(name: String, content: String): MPFile {
        FileSystem.SYSTEM.createDirectories(snippetDir)
        val path = snippetDir / name
        FileSystem.SYSTEM.write(path) { writeUtf8(content) }
        return MPFile(path.toString())
    }

    private fun rel(r: ParseResult, t: RelationType) = r.edges.filter { it.relation == t }

    private fun names(r: ParseResult, t: SymbolType) = r.symbols.filter { it.type == t }.map { it.name }

    private fun one(r: ParseResult, t: SymbolType, n: String) =
        r.symbols.filter { it.type == t && it.name == n }.also { assertTrue(it.isNotEmpty(), "expected $t '$n'") }[0]

    @Test
    fun kotlinExtraction() {
        val code = """
            package com.sample

            import dev.other.Helper

            class Greeter : Base() {
                fun greet(name: String): String {
                    log("greeting")
                    return Helper.build(name)
                }
            }

            fun main() {
                greetAll()
            }

            fun greetAll() {
            }
        """.trimIndent()

        val r = KotlinJavaExtractor().extract(snippet("Greeter.kt", code))

        assertEquals("Kotlin", r.sourceFile.language)
        assertEquals("com.sample", r.sourceFile.packageName)

        assertEquals(listOf("Greeter"), names(r, SymbolType.CLASS))
        assertEquals(setOf("greet", "main", "greetAll"), names(r, SymbolType.FUNCTION).toSet())

        val greeter = one(r, SymbolType.CLASS, "Greeter")
        assertEquals("com.sample.Greeter", greeter.id)
        assertEquals(5, greeter.lineNumber)

        val main = one(r, SymbolType.FUNCTION, "main")
        assertEquals("com.sample#main", main.id)
        assertEquals(12, main.lineNumber)

        assertEquals(listOf("dev.other.Helper"), rel(r, RelationType.IMPORTS).map { it.targetId })
        assertEquals(listOf("Base"), rel(r, RelationType.EXTENDS).map { it.targetId })

        val calls = rel(r, RelationType.CALLS).map { it.sourceId to it.targetId }.toSet()
        assertEquals(
            setOf(
                "com.sample#greet" to "REF:log",
                "com.sample#main" to "REF:greetAll"
            ),
            calls
        )

        assertEquals(4, rel(r, RelationType.CONTAINS).size)
    }

    @Test
    fun kotlinPrimaryConstructorDoesNotProduceFalseExtends() {
        val code = """
            package com.sample

            class Repo(val name: String, val count: Int) : Base(), AutoCloseable {
                override fun close() {}
            }
        """.trimIndent()

        val r = KotlinJavaExtractor().extract(snippet("Repo.kt", code))

        assertEquals(listOf("Repo"), names(r, SymbolType.CLASS))
        val extends = rel(r, RelationType.EXTENDS).map { it.targetId }
        assertEquals(setOf("Base", "AutoCloseable"), extends.toSet())
        assertFalse(extends.any { it.endsWith(")") }, "constructor param types must not leak into EXTENDS: $extends")
        assertFalse(extends.contains("String"), "constructor param types must not leak into EXTENDS: $extends")
    }

    @Test
    fun javaExtraction() {
        val code = """
            package com.sample;

            import java.util.List;

            public class Repo implements Store {
                public Store load() {
                    return null;
                }
            }

            interface Store {
            }
        """.trimIndent()

        val r = KotlinJavaExtractor().extract(snippet("Repo.java", code))

        assertEquals("Java", r.sourceFile.language)
        assertEquals("com.sample", r.sourceFile.packageName)

        assertEquals(listOf("Repo"), names(r, SymbolType.CLASS))
        assertEquals(listOf("Store"), names(r, SymbolType.INTERFACE))
        assertEquals(listOf("load"), names(r, SymbolType.FUNCTION))

        val repo = one(r, SymbolType.CLASS, "Repo")
        assertEquals("com.sample.Repo", repo.id)
        assertEquals(5, repo.lineNumber)

        assertEquals(listOf("java.util.List"), rel(r, RelationType.IMPORTS).map { it.targetId })
        assertEquals(listOf("Store"), rel(r, RelationType.EXTENDS).map { it.targetId })
        assertEquals(3, rel(r, RelationType.CONTAINS).size)
    }

    @Test
    fun rustExtraction() {
        val code = """
            use std::collections::HashMap;

            pub struct Index {
                pub total: u32,
            }

            pub trait Store {
                fn put(&self);
            }

            impl Store for Index {
                fn put(&self) {
                    let m: HashMap<u32, u32> = HashMap::new();
                }
            }
        """.trimIndent()

        val r = RustExtractor().extract(snippet("lib.rs", code))

        assertEquals("Rust", r.sourceFile.language)
        assertEquals("crate", r.sourceFile.packageName)

        assertEquals(listOf("Index"), names(r, SymbolType.CLASS))
        assertEquals(listOf("Store"), names(r, SymbolType.INTERFACE))
        assertEquals(listOf("put"), names(r, SymbolType.FUNCTION))

        assertEquals(listOf("std::collections::HashMap"), rel(r, RelationType.IMPORTS).map { it.targetId })
        assertEquals(listOf("Store"), rel(r, RelationType.EXTENDS).map { it.targetId })

        val src = r.sourceFile.path
        val contains = rel(r, RelationType.CONTAINS).map { it.sourceId to it.targetId }.toSet()
        assertEquals(
            setOf(
                src to "Index",
                src to "Store",
                "Index" to "put"
            ),
            contains
        )
    }

    @Test
    fun goExtraction() {
        val code = """
            package store

            import (
                "fmt"
                "os"
            )

            type Repo struct {
                Name string
            }

            type Loader interface {
                Load()
            }

            func NewRepo() *Repo {
                return &Repo{}
            }
        """.trimIndent()

        val r = GoExtractor().extract(snippet("store.go", code))

        assertEquals("Go", r.sourceFile.language)
        assertEquals("store", r.sourceFile.packageName)

        assertEquals(listOf("Repo"), names(r, SymbolType.CLASS))
        assertEquals(listOf("Loader"), names(r, SymbolType.INTERFACE))
        assertEquals(listOf("NewRepo"), names(r, SymbolType.FUNCTION))

        val loader = one(r, SymbolType.INTERFACE, "Loader")
        assertEquals("store.Loader", loader.id)
        assertEquals(12, loader.lineNumber)

        assertEquals(setOf("fmt", "os"), rel(r, RelationType.IMPORTS).map { it.targetId }.toSet())
    }

    @Test
    fun cExtraction() {
        val code = """
            #include <stdio.h>
            #include "local.h"

            struct Point {
                int x;
            };

            int add(int a, int b) {
                return a + b;
            }
        """.trimIndent()

        val r = CExtractor().extract(snippet("geom.c", code))

        assertEquals("C", r.sourceFile.language)
        assertEquals(listOf("Point"), names(r, SymbolType.CLASS))
        assertEquals(listOf("add"), names(r, SymbolType.FUNCTION))

        val add = one(r, SymbolType.FUNCTION, "add")
        assertEquals(8, add.lineNumber)

        assertEquals(setOf("stdio.h", "local.h"), rel(r, RelationType.IMPORTS).map { it.targetId }.toSet())
        assertTrue(rel(r, RelationType.CONTAINS).size >= 2)
    }

    @Test
    fun cppExtraction() {
        val code = """
            #include <vector>

            namespace app {

            class Engine {
            public:
                void start();
            };

            }

            void Engine::start() {
            }
        """.trimIndent()

        val r = CppExtractor().extract(snippet("engine.cpp", code))

        assertEquals("C++", r.sourceFile.language)
        assertEquals(listOf("Engine"), names(r, SymbolType.CLASS))
        assertEquals(listOf("start"), names(r, SymbolType.FUNCTION))
        assertEquals(listOf("vector"), rel(r, RelationType.IMPORTS).map { it.targetId })
    }

    @Test
    fun csharpExtraction() {
        val code = """
            using System;

            namespace Sample
            {
                public class Widget : IWidget
                {
                    public void Run()
                    {
                    }
                }

                public interface IWidget
                {
                }
            }
        """.trimIndent()

        val r = CSharpExtractor().extract(snippet("Widget.cs", code))

        assertEquals("C#", r.sourceFile.language)
        assertEquals("Sample", r.sourceFile.packageName)

        assertEquals(listOf("Widget"), names(r, SymbolType.CLASS))
        assertEquals(listOf("IWidget"), names(r, SymbolType.INTERFACE))
        assertEquals(listOf("Run"), names(r, SymbolType.FUNCTION))

        val widget = one(r, SymbolType.CLASS, "Widget")
        assertEquals("Sample.Widget", widget.id)

        val run = one(r, SymbolType.FUNCTION, "Run")
        assertEquals("Sample#Run", run.id)

        assertEquals(listOf("System"), rel(r, RelationType.IMPORTS).map { it.targetId })
        assertEquals(listOf("IWidget"), rel(r, RelationType.EXTENDS).map { it.targetId })
    }

    @Test
    fun javascriptExtraction() {
        val code = """
            import { helper } from './helper.js';

            export class Client {
                fetch() {
                    return helper();
                }
            }

            function boot() {
                const c = new Client();
                c.fetch();
            }
        """.trimIndent()

        val r = JavaScriptExtractor().extract(snippet("client.js", code))

        assertEquals("JavaScript", r.sourceFile.language)
        assertEquals("js_module", r.sourceFile.packageName)

        assertEquals(listOf("Client"), names(r, SymbolType.CLASS))
        assertEquals(setOf("fetch", "boot"), names(r, SymbolType.FUNCTION).toSet())

        assertEquals(listOf("./helper.js"), rel(r, RelationType.IMPORTS).map { it.targetId })

        val calls = rel(r, RelationType.CALLS).map { it.sourceId to it.targetId }.toSet()
        assertEquals(
            setOf(
                "fetch" to "REF:helper",
                "boot" to "REF:Client",
                "boot" to "REF:c.fetch"
            ),
            calls
        )
    }

    @Test
    fun typescriptExtraction() {
        val code = """
            export interface Shape {
                area(): number;
            }

            export class Circle implements Shape {
                area(): number {
                    return 1;
                }
            }
        """.trimIndent()

        val r = JavaScriptExtractor().extract(snippet("shape.ts", code))

        assertEquals("TypeScript", r.sourceFile.language)
        assertEquals(listOf("Circle"), names(r, SymbolType.CLASS))
        assertEquals(listOf("Shape"), names(r, SymbolType.INTERFACE))
        assertEquals(listOf("area"), names(r, SymbolType.FUNCTION))
    }

    @Test
    fun cssExtraction() {
        val code = """
            .card {
                color: red;
            }

            #main-title {
                font-weight: bold;
            }
        """.trimIndent()

        val r = CssExtractor().extract(snippet("ui.css", code))

        assertEquals("CSS", r.sourceFile.language)
        assertEquals(setOf("card", "main-title"), names(r, SymbolType.CLASS).toSet())
        assertEquals(2, rel(r, RelationType.CONTAINS).size)
    }
}
