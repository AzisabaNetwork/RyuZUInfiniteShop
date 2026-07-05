package ryuzuinfiniteshop.ryuzuinfiniteshop.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import ryuzuinfiniteshop.ryuzuinfiniteshop.RyuZUInfiniteShop;
import ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigTest {

    private static ServerMock server;
    private Path tempDir;
    private RyuZUInfiniteShop pluginMock;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        tempDir = Files.createTempDirectory("sis-config-test");

        // Mock the plugin
        pluginMock = mock(RyuZUInfiniteShop.class);
        when(pluginMock.getDataFolder()).thenReturn(tempDir.toFile());
        when(pluginMock.getServer()).thenReturn(server);
        when(pluginMock.getLogger()).thenReturn(java.util.logging.Logger.getLogger("SIS"));

        // Set static plugin field
        var field = RyuZUInfiniteShop.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(null, pluginMock);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up temp dir
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }

        // Reset static fields
        Config.autoSaveInterval = 0;
        Config.editLog = true;
        Config.tradeLog = true;
        Config.saveByMMID = true;
        Config.overwriteConverting = false;
        Config.defaultSearchableInConverting = true;
        Config.followPlayer = false;
        Config.language = null;
        Config.readOnlyIgnoreIOException = false;

        MockBukkit.unmock();
    }

    @Test
    void loadDefaultsWhenNoConfigFile() {
        // No config.yml exists yet - FileUtil.initializeFile creates an empty one
        // load() should use all defaults since file is empty
        assertDoesNotThrow(Config::load);

        assertEquals(0, Config.autoSaveInterval);
        assertTrue(Config.editLog);
        assertTrue(Config.tradeLog);
        assertTrue(Config.saveByMMID);
        assertFalse(Config.overwriteConverting);
        assertTrue(Config.defaultSearchableInConverting);
        assertFalse(Config.followPlayer);
        assertFalse(Config.readOnlyIgnoreIOException);
    }

    @Test
    void loadReadsValuesFromConfigFile() throws IOException {
        String yamlContent = "" +
                "AutoSaveInterval: 300\n" +
                "EditLog: false\n" +
                "TradeLog: false\n" +
                "SaveByMMID: false\n" +
                "OverwriteConverting: true\n" +
                "DefaultSearchableInConverting: false\n" +
                "FollowPlayer: true\n" +
                "Language: japanese\n" +
                "ReadOnlyIgnoreIOException: true\n";
        writeConfigFile(yamlContent);

        Config.load();

        assertEquals(300, Config.autoSaveInterval);
        assertFalse(Config.editLog);
        assertFalse(Config.tradeLog);
        assertFalse(Config.saveByMMID);
        assertTrue(Config.overwriteConverting);
        assertFalse(Config.defaultSearchableInConverting);
        assertTrue(Config.followPlayer);
        assertEquals("japanese", Config.language);
        assertTrue(Config.readOnlyIgnoreIOException);
    }

    @Test
    void loadPartialConfigUsesDefaultsForMissingKeys() throws IOException {
        String yamlContent = "" +
                "AutoSaveInterval: 120\n" +
                "Language: english\n";
        writeConfigFile(yamlContent);

        Config.load();

        assertEquals(120, Config.autoSaveInterval);
        assertEquals("english", Config.language);
        // These should be defaults
        assertTrue(Config.editLog);
        assertTrue(Config.tradeLog);
        assertTrue(Config.saveByMMID);
        assertFalse(Config.overwriteConverting);
        assertTrue(Config.defaultSearchableInConverting);
        assertFalse(Config.followPlayer);
        assertFalse(Config.readOnlyIgnoreIOException);
    }

    @Test
    void saveWritesConfigFile() {
        Config.autoSaveInterval = 600;
        Config.editLog = false;
        Config.language = "japanese";

        assertDoesNotThrow(Config::save);

        // Verify the file was created and reload it
        Config.autoSaveInterval = 0;
        Config.load();

        assertEquals(600, Config.autoSaveInterval);
        assertFalse(Config.editLog);
    }

    @Test
    void saveDoesNotOverwriteExistingValues() throws IOException {
        // First create a config with some values
        String yamlContent = "" +
                "AutoSaveInterval: 500\n" +
                "EditLog: true\n";
        writeConfigFile(yamlContent);

        Config.load();
        assertEquals(500, Config.autoSaveInterval);

        // Save should keep existing values
        Config.save();

        // Reload to verify
        Config.load();
        assertEquals(500, Config.autoSaveInterval);
    }

    @Test
    void languageDefaultsToLowerCase() throws IOException {
        String yamlContent = "Language: JAPANESE\n";
        writeConfigFile(yamlContent);

        Config.load();

        assertEquals("japanese", Config.language);
    }

    private void writeConfigFile(String content) throws IOException {
        // FileUtil.initializeFile("config.yml") creates in tempDir
        // But we need to write our content before loading
        File configFile = new File(tempDir.toFile(), "config.yml");
        configFile.getParentFile().mkdirs();
        Files.writeString(configFile.toPath(), content);
    }
}
