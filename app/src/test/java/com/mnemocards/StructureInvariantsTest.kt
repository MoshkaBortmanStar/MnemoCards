package com.mnemocards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T20: финальная проверка инвариантов структуры (S22, S24, S25).
 *
 * Тест читает исходники модуля app (рабочая директория unit-тестов —
 * корень модуля) и проверяет чек-лист финальной задачи:
 * - S24: один NavHost, один AppContainer, одна Application, одна
 *   MainActivity; экран и ViewModel настроек — в ui/syncsettings;
 *   сеть и хранилища токенов — в data; Room-сущности (*Entity)
 *   не покидают пакет data; в экранах настроек нет прямых Color(0x…);
 * - S25: в манифесте нет usesCleartextTraffic и networkSecurityConfig;
 * - S22: в data/network нет ручных сетевых моделей (data class) —
 *   DTO только сгенерированные из specs/openapi.yaml.
 */
class StructureInvariantsTest {

    private val moduleDir = File(".").absoluteFile.parentFile
    private val mainJava = File(moduleDir, "src/main/java")
    private val manifest = File(moduleDir, "src/main/AndroidManifest.xml")

    private fun kotlinFiles(root: File = mainJava): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun occurrences(pattern: String, root: File = mainJava): Int =
        kotlinFiles(root).count { it.readText().contains(pattern) }

    // S24: единственность узловых элементов.
    @Test
    fun `exactly one nav host app container application and activity`() {
        assertEquals("В проекте один NavHost (S24)", 1, occurrences("fun AppNavHost("))
        assertEquals(
            "В проекте один AppContainer (S24)",
            1,
            kotlinFiles().count { it.name == "AppContainer.kt" },
        )
        assertEquals(
            "В проекте один MnemoCardsApplication (S24)",
            1,
            kotlinFiles().count { it.name == "MnemoCardsApplication.kt" },
        )
        assertEquals(
            "В проекте одна MainActivity (S24)",
            1,
            kotlinFiles().count { it.name == "MainActivity.kt" },
        )
    }

    // S24: раскладка по пакетам.
    @Test
    fun `sync settings screen and viewmodel live in ui syncsettings`() {
        val uiSyncSettings = File(mainJava, "com/mnemocards/ui/syncsettings")
        assertTrue(
            "Экран настроек должен лежать в com.mnemocards.ui.syncsettings (S24)",
            File(uiSyncSettings, "SyncSettingsScreen.kt").isFile,
        )
        assertTrue(
            "ViewModel настроек должен лежать в com.mnemocards.ui.syncsettings (S24)",
            File(uiSyncSettings, "SyncSettingsViewModel.kt").isFile,
        )
    }

    @Test
    fun `network api and token storage live in data`() {
        assertTrue(
            "Сетевой API должен лежать в com.mnemocards.data (S24)",
            File(mainJava, "com/mnemocards/data/network/SyncApi.kt").isFile,
        )
        assertTrue(
            "Хранилище токенов должен лежать в com.mnemocards.data (S24)",
            File(mainJava, "com/mnemocards/data/syncsettings/TokenStorage.kt").isFile,
        )
    }

    // S24: Room-сущности не покидают пакет data.
    @Test
    fun `room entities stay inside data package`() {
        val outsideData = kotlinFiles()
            .filter { !it.path.contains("com/mnemocards/data") }
            .filter { it.name.endsWith("Entity.kt") }
        assertTrue(
            "Room-сущности не должны покидать пакет data (S24): $outsideData",
            outsideData.isEmpty(),
        )
    }

    // S24: на экране настроек нет прямых цветов.
    @Test
    fun `sync settings screen uses theme tokens only`() {
        val uiFiles = kotlinFiles(File(mainJava, "com/mnemocards/ui/syncsettings"))
        assertTrue(uiFiles.isNotEmpty())
        uiFiles.forEach { file ->
            assertFalse(
                "Прямые Color(0x…) запрещены в ${file.name} (S24)",
                file.readText().contains("Color(0x"),
            )
        }
    }

    // S25: манифест без cleartext-трафика.
    @Test
    fun `manifest has no cleartext traffic opt-in`() {
        assertTrue("AndroidManifest.xml не найден", manifest.isFile)
        val text = manifest.readText()
        assertFalse(
            "usesCleartextTraffic запрещён (S25)",
            text.contains("usesCleartextTraffic"),
        )
        assertFalse(
            "networkSecurityConfig запрещён (S25)",
            text.contains("networkSecurityConfig"),
        )
    }

    // S22: сетевые модели — только сгенерированные DTO, ручных нет.
    @Test
    fun `no manual network models in data network`() {
        val networkFiles = kotlinFiles(File(mainJava, "com/mnemocards/data/network"))
        assertTrue("Каталог data/network не найден", networkFiles.isNotEmpty())
        networkFiles.forEach { file ->
            assertFalse(
                "Ручные сетевые модели запрещены в ${file.name} — DTO только сгенерированные (S22)",
                file.readText().contains("data class"),
            )
        }
    }
}
