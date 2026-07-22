# Add-on Config, FastInput, Texture LOD, Low Latency Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild lost per-add-on JSON config persistence with a generic right-click config screen, add a `FastInput` add-on for tick-decoupled keyboard latching and raw mouse motion, add a mip-LOD-bias texture setting, and add a Low Latency Mode video setting that reduces swapchain frame-queue depth.

**Architecture:** Four largely independent slices touching a Fabric/Vulkan Minecraft mod (`VulkanMod` fork). Task 1 is pure Java (Gson + file I/O, no Minecraft/Vulkan classes touched at runtime) and is unit-testable in isolation — it's the foundation Task 2 builds on. Tasks 3, 4, and 5 are independent of 1/2 and of each other, except 4 and 5 both edit `Options.java` so 5 is sequenced after 4 to avoid merge conflicts.

**Tech Stack:** Java 21, Fabric Loom, Mixin, Gson (already a transitive dependency via Fabric/Minecraft — no new dependency needed for it), LWJGL/GLFW, Vulkan (LWJGL bindings). JUnit 5 is added in Task 1 for the first time in this project.

## Global Constraints

- Follow existing package conventions: add-on framework code lives under `net.voidsmp.client.addons`; ClickGUI code under `net.voidsmp.client.clickgui`; mixins for this fork's own features (not upstream VulkanMod) under `net.voidsmp.client.mixin`, registered in `src/main/resources/voidclient.mixins.json`. VulkanMod's own config/options code lives under `net.vulkanmod.config`.
- No test framework exists yet in this repo (confirmed: no `src/test`, no JUnit in `build.gradle`). Task 1 adds a minimal JUnit 5 setup. Tasks 2-5 touch Minecraft/Vulkan runtime classes that require a running client to exercise — they are verified via manual `runClient` testing (this project's established pattern — see prior optimization work notes), not automated tests. Don't invent a mocking framework to force-fit automated tests onto GLFW/Vulkan calls.
- This is a Minecraft mod: don't add generic framework abstractions beyond what each task needs. No speculative "addon reload" mechanism — one doesn't exist in this codebase today (add-ons are constructed once at client init and never re-instantiated), so don't build for it.
- Commit after each task, following this repo's existing commit style (see `git log`).

---

### Task 1: ConfigEntry listener removal + AddonConfigStorage (persistence core)

**Owner: user (pure Java, no Vulkan/GLSL/graphics — Gson + file I/O + a plain-Java test double).**

**Files:**
- Modify: `build.gradle` (add JUnit 5 test dependency + test block)
- Modify: `src/main/java/net/voidsmp/client/addons/models/ConfigEntry.java`
- Create: `src/main/java/net/voidsmp/client/addons/config/AddonConfigStorage.java`
- Test: `src/test/java/net/voidsmp/client/addons/models/ConfigEntryTest.java`
- Test: `src/test/java/net/voidsmp/client/addons/config/AddonConfigStorageTest.java`

**Interfaces:**
- Produces: `ConfigEntry<T>.addListener(Consumer<T> callback) -> AutoCloseable` (registers a listener *without* firing it immediately, unlike the existing `onChange`; returns a handle whose `close()` removes it).
- Produces: `AddonConfigStorage.load(Addon addon)` — reads `config/VoidAddons/<sanitized-id>.json` if present and applies matching values onto `addon.config` entries by `ConfigEntry.id`; no-ops if the file doesn't exist; unknown keys on disk are ignored.
- Produces: `AddonConfigStorage.save(Addon addon)` — writes all of `addon.config` to that same file.
- Produces: `AddonConfigStorage.setConfigDir(Path dir)` — overrides the storage root (used by tests; production default is `config/VoidAddons` resolved from the current working directory, matching how `Config.java` resolves `FabricLoader.getInstance().getConfigDir()` — Task 2 will point this at the real Fabric config dir at startup).

- [ ] **Step 1: Add JUnit 5 to the build**

Edit `build.gradle`. Add inside the existing `dependencies { ... }` block near the top (the one with `minecraft`, `mappings`, `modImplementation` — not the LWJGL one further down):

```gradle
	testImplementation platform('org.junit:junit-bom:5.10.2')
	testImplementation 'org.junit.jupiter:junit-jupiter'
```

Add a new top-level block anywhere after the `dependencies` blocks:

```gradle
test {
	useJUnitPlatform()
}
```

- [ ] **Step 2: Run the (currently empty) test task to confirm the setup works**

Run: `./gradlew.bat test --console=plain` (Windows; use `./gradlew test` on other shells if you're not on the PowerShell/Bash tool default)
Expected: `BUILD SUCCESSFUL` — no tests exist yet, so nothing runs, but this confirms the JUnit platform wiring didn't break the build.

- [ ] **Step 3: Write the failing test for listener removal**

Create `src/test/java/net/voidsmp/client/addons/models/ConfigEntryTest.java`:

```java
package net.voidsmp.client.addons.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ConfigEntryTest {

    @Test
    void addListenerDoesNotFireImmediately() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] callCount = {0};

        entry.addListener(v -> callCount[0]++);

        assertEquals(0, callCount[0], "addListener should not fire on registration, unlike onChange");
    }

    @Test
    void addListenerFiresOnSubsequentChanges() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] lastValue = {-1};

        entry.addListener(v -> lastValue[0] = v);
        entry.set(5);

        assertEquals(5, lastValue[0]);
    }

    @Test
    void closingTheSubscriptionStopsFutureNotifications() {
        ConfigEntry<Integer> entry = new ConfigEntry<>("x", ConfigEntryType.INT, 0);
        int[] callCount = {0};
        AutoCloseable subscription = entry.addListener(v -> callCount[0]++);

        entry.set(1);
        assertEquals(1, callCount[0]);

        try {
            subscription.close();
        } catch (Exception e) {
            fail(e);
        }

        entry.set(2);
        assertEquals(1, callCount[0], "listener should not fire after being removed");
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew.bat test --tests "net.voidsmp.client.addons.models.ConfigEntryTest" --console=plain`
Expected: FAIL — compile error, `addListener` is not defined on `ConfigEntry`.

- [ ] **Step 5: Implement `addListener` on ConfigEntry**

In `src/main/java/net/voidsmp/client/addons/models/ConfigEntry.java`, add this method (near the existing `onChange`, e.g. right after it):

```java
    /**
     * Registers a change listener without firing it immediately (unlike
     * {@link #onChange}), returning a handle that removes it. Meant for code
     * that attaches/detaches listeners across a lifecycle it doesn't fully
     * control — e.g. config persistence — where onChange's fire-on-attach
     * behavior would misfire a write, and where accumulating listeners with
     * no way to remove them would leak.
     */
    public AutoCloseable addListener(Consumer<T> callback) {
        listeners.add(callback);
        return () -> listeners.remove(callback);
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew.bat test --tests "net.voidsmp.client.addons.models.ConfigEntryTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 7: Commit**

```bash
git add build.gradle src/main/java/net/voidsmp/client/addons/models/ConfigEntry.java src/test/java/net/voidsmp/client/addons/models/ConfigEntryTest.java
git commit -m "Add JUnit 5 and a removable listener API to ConfigEntry"
```

- [ ] **Step 8: Write the failing tests for AddonConfigStorage**

Create `src/test/java/net/voidsmp/client/addons/config/AddonConfigStorageTest.java`:

```java
package net.voidsmp.client.addons.config;

import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AddonConfigStorageTest {

    @TempDir
    Path tempDir;

    static class FakeAddon extends Addon {
        FakeAddon() {
            this.id = "voidclient:fake";
            this.name = "Fake";
            this.config = new ConfigEntry[]{
                new ConfigEntry<>("enabled", ConfigEntryType.BOOLEAN, false),
                new ConfigEntry<>("intensity", ConfigEntryType.FLOAT, 1.0f).range(0.0, 2.0)
            };
        }

        @Override
        protected void onRegister(Consumer<Addon> callback) {
        }

        @Override
        protected void onConfigUpdate(Consumer<ConfigEntry> change) {
        }
    }

    @SuppressWarnings("unchecked")
    private static ConfigEntry<Boolean> enabledEntry(Addon addon) {
        return (ConfigEntry<Boolean>) addon.config[0];
    }

    @SuppressWarnings("unchecked")
    private static ConfigEntry<Float> intensityEntry(Addon addon) {
        return (ConfigEntry<Float>) addon.config[1];
    }

    @BeforeEach
    void setUp() {
        AddonConfigStorage.setConfigDir(tempDir);
    }

    @Test
    void saveThenLoadRoundTripsValues() {
        FakeAddon addon = new FakeAddon();
        enabledEntry(addon).set(true);
        intensityEntry(addon).set(1.5f);

        AddonConfigStorage.save(addon);

        FakeAddon reloaded = new FakeAddon();
        AddonConfigStorage.load(reloaded);

        assertEquals(true, enabledEntry(reloaded).get());
        assertEquals(1.5f, intensityEntry(reloaded).get());
    }

    @Test
    void loadWithNoFileOnDiskLeavesDefaults() {
        FakeAddon addon = new FakeAddon();
        AddonConfigStorage.load(addon);

        assertFalse(enabledEntry(addon).get());
        assertEquals(1.0f, intensityEntry(addon).get());
    }

    @Test
    void loadIgnoresKeysNotPresentInCurrentConfig() {
        FakeAddon addon = new FakeAddon();
        enabledEntry(addon).set(true);
        AddonConfigStorage.save(addon);

        // A future version of the addon might drop a key; loading an addon
        // whose config array no longer has every key that's on disk must not
        // throw.
        FakeAddon reloaded = new FakeAddon();
        AddonConfigStorage.load(reloaded);
        assertEquals(true, enabledEntry(reloaded).get());
    }
}
```

- [ ] **Step 9: Run the tests to verify they fail**

Run: `./gradlew.bat test --tests "net.voidsmp.client.addons.config.AddonConfigStorageTest" --console=plain`
Expected: FAIL — compile error, `net.voidsmp.client.addons.config` package / `AddonConfigStorage` class doesn't exist.

- [ ] **Step 10: Implement AddonConfigStorage**

Create `src/main/java/net/voidsmp/client/addons/config/AddonConfigStorage.java`:

```java
package net.voidsmp.client.addons.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists each add-on's {@link ConfigEntry} values to its own JSON file
 * under {@code config/VoidAddons/}, so settings survive a restart. Loading
 * and saving are explicit, one-shot operations — callers decide when they
 * run (e.g. once at register time, and on each config change thereafter);
 * this class holds no listeners itself and can't leak them.
 */
public final class AddonConfigStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configDir = Path.of("config", "VoidAddons");

    private AddonConfigStorage() {
    }

    /** Overrides where config files are read from/written to. Used by tests and by startup wiring. */
    public static void setConfigDir(Path dir) {
        configDir = dir;
    }

    private static Path fileFor(Addon addon) {
        return configDir.resolve(addon.id.replace(':', '_') + ".json");
    }

    public static void load(Addon addon) {
        Path file = fileFor(addon);
        if (!Files.exists(file)) {
            return;
        }

        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read addon config: " + file, e);
        }

        for (ConfigEntry<?> entry : addon.config) {
            if (json.has(entry.id)) {
                applyValue(entry, json.get(entry.id));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyValue(ConfigEntry<T> entry, JsonElement element) {
        Object value = switch (entry.type) {
            case BOOLEAN -> element.getAsBoolean();
            case STRING -> element.getAsString();
            case FLOAT, UFLOAT -> element.getAsFloat();
            case SHORT, USHORT -> element.getAsShort();
            case INT, UINT -> element.getAsInt();
            case LONG, ULONG -> element.getAsLong();
            case BYTE, UBYTE -> element.getAsByte();
        };
        entry.set((T) value);
    }

    public static void save(Addon addon) {
        JsonObject json = new JsonObject();
        for (ConfigEntry<?> entry : addon.config) {
            json.add(entry.id, GSON.toJsonTree(entry.get()));
        }

        Path file = fileFor(addon);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write addon config: " + file, e);
        }
    }
}
```

- [ ] **Step 11: Run the tests to verify they pass**

Run: `./gradlew.bat test --tests "net.voidsmp.client.addons.config.AddonConfigStorageTest" --console=plain`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 12: Run the full test suite**

Run: `./gradlew.bat test --console=plain`
Expected: `BUILD SUCCESSFUL`, 6 tests passed total (3 from `ConfigEntryTest`, 3 from `AddonConfigStorageTest`).

- [ ] **Step 13: Commit**

```bash
git add src/main/java/net/voidsmp/client/addons/config/AddonConfigStorage.java src/test/java/net/voidsmp/client/addons/config/AddonConfigStorageTest.java
git commit -m "Add AddonConfigStorage for per-addon JSON config persistence"
```

---

### Task 2: Wire persistence into AddonRegistry + generic config screen + right-click

**Files:**
- Modify: `src/main/java/net/voidsmp/client/addons/AddonRegistry.java`
- Modify: `src/main/java/net/voidsmp/client/VoidClient.java` (point `AddonConfigStorage` at the real Fabric config dir at startup)
- Create: `src/main/java/net/voidsmp/client/clickgui/AddonConfigScreen.java`
- Modify: `src/main/java/net/voidsmp/client/clickgui/AddonTile.java`

**Interfaces:**
- Consumes: `AddonConfigStorage.load(Addon)`, `AddonConfigStorage.save(Addon)`, `AddonConfigStorage.setConfigDir(Path)` (Task 1), `ConfigEntry<T>.addListener(Consumer<T>) -> AutoCloseable` (Task 1).
- Consumes: `Addon.config` (`ConfigEntry[]`), `ConfigEntry.type`/`min()`/`max()`/`friendlyName`/`get()`/`set(T)` (existing).

- [ ] **Step 1: Point AddonConfigStorage at the real config directory on startup**

Read `src/main/java/net/voidsmp/client/VoidClient.java` first to find its init method (it's where `AddonRegistry.register(new TestAddon())` etc. are called, per `grep -n "AddonRegistry.register" src/main/java/net/voidsmp/client/VoidClient.java`). Add, before the first `AddonRegistry.register(...)` call:

```java
net.voidsmp.client.addons.config.AddonConfigStorage.setConfigDir(
    net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("VoidAddons")
);
```

(Use a proper top-of-file import instead of fully-qualified names if the file's existing style uses imports — match whatever's already there.)

- [ ] **Step 2: Wire load + autosave into AddonRegistry.register**

Replace the full contents of `src/main/java/net/voidsmp/client/addons/AddonRegistry.java` with:

```java
package net.voidsmp.client.addons;

import net.voidsmp.client.addons.config.AddonConfigStorage;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central list of registered add-ons. The ClickGUI reads this to lay out its
 * tile grid; add-ons register themselves at client init.
 */
public final class AddonRegistry {
    private static final List<Addon> ADDONS = new ArrayList<>();

    private AddonRegistry() {}

    public static void register(Addon addon) {
        ADDONS.add(addon);

        AddonConfigStorage.load(addon);
        for (ConfigEntry<?> entry : addon.config) {
            // Registry owns this listener, not the addon — the addon never
            // holds a reference to anything persistence-related, so there's
            // nothing for it to leak. Add-ons are only ever registered once
            // (constructed at client init, never re-instantiated), so this
            // never needs to be detached.
            entry.addListener(v -> AddonConfigStorage.save(addon));
        }

        addon.onRegister(a -> {});
    }

    public static List<Addon> all() {
        return Collections.unmodifiableList(ADDONS);
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew.bat compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Read AbstractWidget/AbstractButton click handling to confirm the right-click hook point**

Read `src/main/java/net/voidsmp/client/clickgui/AddonTile.java` (already shown in this plan's investigation — it only overrides `onPress(InputWithModifiers)`, called by `AbstractButton.onClick(MouseButtonEvent, boolean)` which just forwards the event straight through). Two things make right-click possible without touching Mojang's classes:
- `AbstractWidget.isValidClickButton(MouseButtonInfo)` defaults to `button() == 0` (left-click only) — override it in `AddonTile` to also accept the right button.
- `onPress`'s `InputWithModifiers input` parameter, when invoked via a mouse click, is actually the `MouseButtonEvent` instance (since `MouseButtonEvent implements InputWithModifiers` and `AbstractButton.onClick` passes it straight to `onPress`) — so it can be pattern-matched to read `.button()`.

- [ ] **Step 5: Create the generic AddonConfigScreen**

Create `src/main/java/net/voidsmp/client/clickgui/AddonConfigScreen.java`:

```java
package net.voidsmp.client.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;

/**
 * Generic, auto-built config screen for any add-on: one row per
 * {@link ConfigEntry}, widget chosen by {@link ConfigEntryType}. No add-on
 * needs its own screen class. Opened by right-clicking an {@link AddonTile}.
 */
public class AddonConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 200;

    private final Addon addon;
    private final Screen parent;

    public AddonConfigScreen(Addon addon, Screen parent) {
        super(Component.literal((addon.name == null ? addon.id : addon.name) + " Settings"));
        this.addon = addon;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int startY = 32;
        int x = (this.width - ROW_WIDTH) / 2;

        if (addon.config.length == 0) {
            return; // renders a "no settings" message instead, see render()
        }

        for (int i = 0; i < addon.config.length; i++) {
            ConfigEntry<?> entry = addon.config[i];
            int y = startY + i * (ROW_HEIGHT + 4);

            if (entry.type == ConfigEntryType.BOOLEAN) {
                addRenderableWidget(booleanWidget(x, y, entry));
            } else {
                addRenderableWidget(rangeWidget(x, y, entry));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private SpriteButton booleanWidget(int x, int y, ConfigEntry<?> entry) {
        ConfigEntry<Boolean> boolEntry = (ConfigEntry<Boolean>) entry;
        return new SpriteButton(x, y, ROW_WIDTH, ROW_HEIGHT,
            label(entry, boolEntry.get() ? "ON" : "OFF"),
            () -> boolEntry.set(!boolEntry.get()));
    }

    @SuppressWarnings("unchecked")
    private AbstractSliderButton rangeWidget(int x, int y, ConfigEntry<?> entry) {
        ConfigEntry<Number> numEntry = (ConfigEntry<Number>) entry;
        double min = numEntry.min() != null ? numEntry.min() : 0.0;
        double max = numEntry.max() != null ? numEntry.max() : 100.0;
        double current = numEntry.get().doubleValue();
        double progress = max > min ? (current - min) / (max - min) : 0.0;

        return new AbstractSliderButton(x, y, ROW_WIDTH, ROW_HEIGHT, label(entry, String.valueOf(numEntry.get())), progress) {
            @Override
            protected void updateMessage() {
                double value = min + (max - min) * this.value;
                setMessage(label(entry, formatValue(entry.type, value)));
            }

            @Override
            protected void applyValue() {
                double value = min + (max - min) * this.value;
                numEntry.set(convert(entry.type, value));
            }
        };
    }

    private static Component label(ConfigEntry<?> entry, String valueText) {
        String name = entry.friendlyName != null ? entry.friendlyName : entry.id;
        return Component.literal(name + ": " + valueText);
    }

    private static String formatValue(ConfigEntryType type, double value) {
        return switch (type) {
            case FLOAT, UFLOAT -> String.format("%.2f", value);
            default -> String.valueOf((long) value);
        };
    }

    private static Number convert(ConfigEntryType type, double value) {
        return switch (type) {
            case FLOAT, UFLOAT -> (float) value;
            case SHORT, USHORT -> (short) value;
            case LONG, ULONG -> (long) value;
            case BYTE, UBYTE -> (byte) value;
            default -> (int) value;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (addon.config.length == 0) {
            graphics.drawCenteredString(this.font, "This add-on has no settings.", this.width / 2, 40, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 6: Wire right-click on AddonTile**

Replace the contents of `src/main/java/net/voidsmp/client/clickgui/AddonTile.java` with:

```java
package net.voidsmp.client.clickgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.voidsmp.client.addons.models.Addon;
import org.lwjgl.glfw.GLFW;

/**
 * Clickable wrapper around an {@link Addon}'s tile. Left-click toggles the
 * add-on on/off; right-click opens its config screen. The visuals are
 * delegated to {@link Addon#renderTile} so an add-on stays in control of how
 * its own tile looks.
 */
public class AddonTile extends AbstractButton {

    private final Addon addon;

    public AddonTile(int x, int y, int width, int height, Addon addon) {
        super(x, y, width, height, Component.literal(addon.name == null ? addon.id : addon.name));
        this.addon = addon;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || info.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (input instanceof MouseButtonEvent event && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AddonConfigScreen(addon, minecraft.screen));
        } else {
            addon.toggle();
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        addon.renderTile(graphics, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
```

- [ ] **Step 7: Build to confirm it compiles**

Run: `./gradlew.bat compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Manual runtime verification**

Run: `./gradlew.bat runClient`
In-game: open the ClickGUI (Right Shift), left-click an add-on tile to confirm toggling still works, then right-click a tile (e.g. MotionBlur, which has config entries) to confirm the config screen opens, shows its entries, and Escape returns to the ClickGUI. Change a value, close the game, reopen, and confirm the value persisted by checking `config/VoidAddons/<id>.json` exists with the new value and reloading shows it in the screen again. Right-click TestAddon (empty config) and confirm it shows "This add-on has no settings." instead of erroring.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/net/voidsmp/client/addons/AddonRegistry.java src/main/java/net/voidsmp/client/VoidClient.java src/main/java/net/voidsmp/client/clickgui/AddonConfigScreen.java src/main/java/net/voidsmp/client/clickgui/AddonTile.java
git commit -m "Wire per-addon config persistence and a right-click config screen"
```

---

### Task 3: FastInput add-on (keyboard latching + raw mouse motion)

**Files:**
- Create: `src/main/java/net/voidsmp/client/addons/FastInputAddon.java`
- Create: `src/main/java/net/voidsmp/client/mixin/FastInputMixin.java`
- Modify: `src/main/resources/voidclient.mixins.json`
- Modify: `src/main/java/net/voidsmp/client/VoidClient.java` (register the add-on)

**Interfaces:**
- Consumes: `Addon` base class, `AddonRegistry.register(Addon)` (existing).
- Produces: `FastInputAddon.INSTANCE` (singleton), `FastInputAddon.INSTANCE.pollIfDue()` — called every frame from `FastInputMixin`.

- [ ] **Step 1: Create the FastInputAddon class**

Create `src/main/java/net/voidsmp/client/addons/FastInputAddon.java`:

```java
package net.voidsmp.client.addons;

import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.models.Addon;
import net.voidsmp.client.addons.models.ConfigEntry;
import net.voidsmp.client.addons.models.ConfigEntryType;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Decouples keyboard sampling from the 20Hz game tick and forces raw mouse
 * motion on while active. Mouse *look* is already per-frame in vanilla
 * (Minecraft.runTick calls MouseHandler.handleAccumulatedMovement, not
 * Minecraft.tick), so this addon doesn't touch mouse look — only keyboard
 * state, which vanilla still only consumes once per tick.
 */
public class FastInputAddon extends Addon {
    public static final FastInputAddon INSTANCE = new FastInputAddon();

    private final ConfigEntry<Integer> pollRateHz =
        new ConfigEntry<>("pollRateHz", ConfigEntryType.INT, 250)
            .range(60, 1000)
            .describe("Poll Rate (Hz)", "How often keyboard state is re-sampled, independent of the 20Hz game tick.");

    private double nextPollTime = Double.NEGATIVE_INFINITY;
    private boolean rawMotionWasEnabled;

    private FastInputAddon() {
        this.id = "voidclient:fast_input";
        this.name = "Fast Input";
        this.config = new ConfigEntry[]{pollRateHz};
    }

    @Override
    protected void onRegister(Consumer<Addon> callback) {
    }

    @Override
    protected void onConfigUpdate(Consumer<ConfigEntry> change) {
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean wasEnabled = isEnabled();
        super.setEnabled(enabled);
        if (enabled && !wasEnabled) {
            onEnabled();
        } else if (!enabled && wasEnabled) {
            onDisabled();
        }
    }

    private void onEnabled() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        rawMotionWasEnabled = GLFW.glfwGetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION) == GLFW.GLFW_TRUE;
        if (GLFW.glfwRawMouseMotionSupported()) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
        }
        nextPollTime = Double.NEGATIVE_INFINITY;
    }

    private void onDisabled() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, rawMotionWasEnabled ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }

    /** Called every rendered frame by {@code FastInputMixin}; internally throttles to {@link #pollRateHz}. */
    public void pollIfDue() {
        if (!isEnabled()) {
            return;
        }

        double now = GLFW.glfwGetTime();
        if (now < nextPollTime) {
            return;
        }
        nextPollTime = now + 1.0 / Math.max(1, pollRateHz.get());

        latchKeyboardState();
    }

    private void latchKeyboardState() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        // Re-reads current GLFW key state directly (bypassing vanilla's
        // queued-since-last-tick callback events) for every key vanilla's
        // KeyMapping system tracks, so the next tick sees the freshest
        // possible state. KeyMapping's own key/press bookkeeping already
        // updates on the GLFW key callback; polling here just forces a fresh
        // read immediately before it matters instead of relying on whatever
        // was last queued.
        for (net.minecraft.client.KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getKey().getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
                continue;
            }
            int glfwKey = mapping.getKey().getValue();
            boolean pressed = GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
            net.minecraft.client.KeyMapping.set(mapping.getKey(), pressed);
        }
    }
}
```

- [ ] **Step 2: Create the mixin that drives polling every frame**

Create `src/main/java/net/voidsmp/client/mixin/FastInputMixin.java`:

```java
package net.voidsmp.client.mixin;

import net.minecraft.client.Minecraft;
import net.voidsmp.client.addons.FastInputAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives {@link FastInputAddon#pollIfDue()} once per rendered frame — the
 * same injection point VulkanMod's own frame mixin uses (runTick fires every
 * frame, not every tick).
 */
@Mixin(Minecraft.class)
public class FastInputMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void voidclient$pollFastInput(boolean renderLevel, CallbackInfo ci) {
        FastInputAddon.INSTANCE.pollIfDue();
    }
}
```

- [ ] **Step 3: Register the mixin**

Edit `src/main/resources/voidclient.mixins.json`, add `"FastInputMixin"` to the `"client"` array (alphabetically, between `"ClientBrandMixin"` and `"LightColorMixin"`... actually between "ClientBrandMixin" and the next entry that sorts after "FastInputMixin" — insert it so the array reads):

```json
  "client": [
    "ClientBrandMixin",
    "FastInputMixin",
    "FpsLimiterMixin",
    "LightColorMixin",
    "LightDataAccessMixin",
    "MotionBlurGuiMixin",
    "SplashOverlayMixin",
    "WindowTitleMixin"
  ],
```

- [ ] **Step 4: Register the add-on**

In `src/main/java/net/voidsmp/client/VoidClient.java`, add alongside the existing `AddonRegistry.register(...)` calls:

```java
AddonRegistry.register(FastInputAddon.INSTANCE);
```

(Add the corresponding import if the file uses explicit imports rather than fully-qualified references — match its existing style.)

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew.bat compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual runtime verification**

Run: `./gradlew.bat runClient`
In-game: open the ClickGUI, confirm "Fast Input" appears as a tile, enable it, right-click to open its config screen and confirm the Poll Rate slider works and persists across restart. With it enabled, play for a few minutes and confirm no crashes, no stuck/ghost key presses, and normal movement (WASD, jump, sprint) still behaves correctly. Disable it and confirm raw mouse motion reverts to whatever it was before (check vanilla's Mouse Settings > Raw Input toggle state is unchanged by having used this addon).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/voidsmp/client/addons/FastInputAddon.java src/main/java/net/voidsmp/client/mixin/FastInputMixin.java src/main/resources/voidclient.mixins.json src/main/java/net/voidsmp/client/VoidClient.java
git commit -m "Add FastInput addon: tick-decoupled keyboard latching + raw mouse motion"
```

---

### Task 4: Texture mip LOD bias

**Files:**
- Modify: `src/main/java/net/vulkanmod/vulkan/texture/SamplerInfo.java`
- Modify: `src/main/java/net/vulkanmod/vulkan/texture/SamplerManager.java`
- Modify: `src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java`
- Modify: `src/main/java/net/vulkanmod/config/Config.java`
- Modify: `src/main/java/net/vulkanmod/config/option/Options.java`
- Modify: `src/main/resources/assets/vulkanmod/lang/en_us.json`

**Interfaces:**
- Produces: `Config.mipLodBiasTenths` (int field, default 0).
- Produces: `SamplerManager.getSampler(..., float mipLodBias)` overload additions threading through to `createTextureSampler`.

- [ ] **Step 1: Add the config field**

In `src/main/java/net/vulkanmod/config/Config.java`, add near the other rendering fields (e.g. right after `public boolean textureAnimations = true;`):

```java
    public int mipLodBiasTenths = 0;
```

- [ ] **Step 2: Extend SamplerInfo's cache key with mip LOD bias**

In `src/main/java/net/vulkanmod/vulkan/texture/SamplerInfo.java`:

Add a field next to `maxLod`/`maxAnisotropy` (after line 14):

```java
    final float mipLodBias;
```

Update the two non-default constructors to accept and store it. Change:

```java
    public SamplerInfo(int addressModeU, int addressModeV,
                       int minFilter, int magFilter, int mipmapMode,
                       float maxLod, boolean anisotropy, float maxAnisotropy,
                       int reductionMode)
    {
        this(addressModeU, addressModeV, minFilter, magFilter, mipmapMode, maxLod,
             anisotropy, maxAnisotropy, false, 0, reductionMode);
    }
```
to:
```java
    public SamplerInfo(int addressModeU, int addressModeV,
                       int minFilter, int magFilter, int mipmapMode,
                       float maxLod, boolean anisotropy, float maxAnisotropy,
                       int reductionMode)
    {
        this(addressModeU, addressModeV, minFilter, magFilter, mipmapMode, maxLod,
             anisotropy, maxAnisotropy, false, 0, reductionMode, 0.0f);
    }
```

Change the full constructor:
```java
    public SamplerInfo(int addressModeU, int addressModeV,
                       int minFilter, int magFilter, int mipmapMode,
                       float maxLod, boolean anisotropy, float maxAnisotropy,
                       boolean compare, int compareOp,
                       int reductionMode)
    {
        this.maxLod = (int) maxLod;
        this.maxAnisotropy = (int) maxAnisotropy;

        this.encodedState = getEncodedState(addressModeU, addressModeV, minFilter, magFilter, mipmapMode,
                                            anisotropy, compare, compareOp, reductionMode);
    }
```
to:
```java
    public SamplerInfo(int addressModeU, int addressModeV,
                       int minFilter, int magFilter, int mipmapMode,
                       float maxLod, boolean anisotropy, float maxAnisotropy,
                       boolean compare, int compareOp,
                       int reductionMode, float mipLodBias)
    {
        this.maxLod = (int) maxLod;
        this.maxAnisotropy = (int) maxAnisotropy;
        this.mipLodBias = mipLodBias;

        this.encodedState = getEncodedState(addressModeU, addressModeV, minFilter, magFilter, mipmapMode,
                                            anisotropy, compare, compareOp, reductionMode);
    }
```

Update the no-arg constructor's delegation (`this(...)` call) to pass `0.0f` as the new final argument too.

Add a getter next to `getMaxLod()`:
```java
    public float getMipLodBias() {
        return mipLodBias;
    }
```

Update `equals`/`hashCode` to include `mipLodBias`:
```java
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        SamplerInfo samplerInfo = (SamplerInfo) o;
        return maxLod == samplerInfo.maxLod && maxAnisotropy == samplerInfo.maxAnisotropy
            && Float.compare(mipLodBias, samplerInfo.mipLodBias) == 0 && encodedState == samplerInfo.encodedState;
    }

    @Override
    public int hashCode() {
        int result = encodedState;
        result = 31 * result + maxLod;
        result = 31 * result + maxAnisotropy;
        result = 31 * result + Float.hashCode(mipLodBias);
        return result;
    }
```

In the `Builder` inner class, add a field `float mipLodBias = 0.0f;` next to the other fields, a `setMipLodBias(float)` method mirroring `setMaxLod`, and pass it as the new final argument in `createSamplerInfo()`'s `new SamplerInfo(...)` call.

- [ ] **Step 3: Thread mipLodBias through SamplerManager**

In `src/main/java/net/vulkanmod/vulkan/texture/SamplerManager.java`, add an overload of `getSampler` that accepts `float mipLodBias`, mirroring the existing 5-arg one:

```java
    public static long getSampler(boolean clamp, boolean linearFiltering, int maxLod, boolean anisotropy, int maxAnisotropy, float mipLodBias) {
        int addressMode = clamp ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT;
        int filter = linearFiltering ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        int mipmapMode = linearFiltering ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;

        SamplerInfo samplerInfo = SamplerInfo.builder()
            .setAddressMode(addressMode)
            .setFiltering(filter, filter, mipmapMode)
            .setMaxLod(maxLod)
            .setMipLodBias(mipLodBias)
            .createSamplerInfo();

        if (anisotropy) {
            samplerInfo = SamplerInfo.builder()
                .setAddressMode(addressMode)
                .setFiltering(filter, filter, mipmapMode)
                .setMaxLod(maxLod)
                .setMipLodBias(mipLodBias)
                .setAnisotropy(maxAnisotropy)
                .createSamplerInfo();
        }

        return getSampler(samplerInfo);
    }
```

(This duplicates the anisotropy-on/off branch rather than trying to make `Builder` fluently conditional — matches this file's existing style of small direct overloads rather than a general-purpose fluent call site.)

In `createTextureSampler`, replace:
```java
            samplerInfo.minLod(0.0F);
            samplerInfo.mipLodBias(0.0F);
```
with:
```java
            samplerInfo.minLod(0.0F);
            samplerInfo.mipLodBias(sampler.getMipLodBias());
```

- [ ] **Step 4: Wire it into the terrain (block atlas) sampler**

In `src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java`, find the block around line 357-365 (the `useAnisotropy`/`maxAnisotropy` reads and the `terrainSampler` creation). Add a `mipLodBias` read next to the existing two:

```java
        boolean useAnisotropy = this.minecraft.options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC;
        int maxAnisotropy = this.minecraft.options.maxAnisotropyValue();
        float mipLodBias = Initializer.CONFIG.mipLodBiasTenths / 10.0f;
```

Change the sampler creation call:
```java
        if (this.terrainSampler == 0L) {
            this.terrainSampler = SamplerManager.getSampler(true, true, texture.getVulkanImage().mipLevels - 1, useAnisotropy, maxAnisotropy);
        }
```
to:
```java
        if (this.terrainSampler == 0L) {
            this.terrainSampler = SamplerManager.getSampler(true, true, texture.getVulkanImage().mipLevels - 1, useAnisotropy, maxAnisotropy, mipLodBias);
        }
```

Add the import for `net.vulkanmod.Initializer` if the file doesn't already have it (check first — other files in this package already reference `Initializer.CONFIG`, e.g. `SectionGraph.java` per prior work, so it's likely already imported somewhere similar; add it if not present).

- [ ] **Step 5: Expose the setting and invalidate the cached sampler on change**

In `src/main/java/net/vulkanmod/config/option/Options.java`, inside `getOptimizationOpts()`, add to the first `OptionBlock`'s `Option<?>[]` array (alongside the existing `hudCache` `SwitchOption`):

```java
                        new RangeOption(Component.translatable("vulkanmod.options.mipLodBias"),
                                -10, 10, 1,
                                v -> Component.literal(String.format("%.1f", v / 10.0)),
                                value -> {
                                    config.mipLodBiasTenths = value;
                                    WorldRenderer.getInstance().resetSampler();
                                },
                                () -> config.mipLodBiasTenths)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.mipLodBias.tooltip"))
```

(Add `import net.vulkanmod.render.chunk.WorldRenderer;` if not already present in this file — check first, `WorldRenderer` may already be imported for other options in this class.)

- [ ] **Step 6: Add the lang entries**

In `src/main/resources/assets/vulkanmod/lang/en_us.json`, add near the `hudCache` entries:

```json
  "vulkanmod.options.mipLodBias": "Mip LOD Bias",
  "vulkanmod.options.mipLodBias.tooltip": "Shifts which mip level distant terrain textures sample from. Negative sharpens (more shimmer at a distance); positive softens distant textures more (less aliasing, slightly less bandwidth).",
```

- [ ] **Step 7: Build to confirm it compiles**

Run: `./gradlew.bat compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Manual runtime verification**

Run: `./gradlew.bat runClient`
In-game: open Optimizations settings, find "Mip LOD Bias", move it to a strongly positive value (e.g. +1.0) and confirm distant terrain visibly softens/blurs more; move it to a strongly negative value and confirm distant terrain gets sharper/shimmerier. Confirm the change applies immediately without a restart (via `resetSampler()`), and confirm it persists across a restart via `vulkanmod_settings.json`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/net/vulkanmod/vulkan/texture/SamplerInfo.java src/main/java/net/vulkanmod/vulkan/texture/SamplerManager.java src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java src/main/java/net/vulkanmod/config/Config.java src/main/java/net/vulkanmod/config/option/Options.java src/main/resources/assets/vulkanmod/lang/en_us.json
git commit -m "Add mip LOD bias sampler setting for terrain texture LOD"
```

---

### Task 5: Low Latency Mode video setting

**Depends on Task 4** (both edit `Options.java`; do this one second to avoid a merge conflict).

**Files:**
- Modify: `src/main/java/net/vulkanmod/config/Config.java`
- Modify: `src/main/java/net/vulkanmod/vulkan/Renderer.java`
- Modify: `src/main/java/net/vulkanmod/config/option/Options.java`
- Modify: `src/main/resources/assets/vulkanmod/lang/en_us.json`

**Interfaces:**
- Produces: `Config.lowLatencyMode` (boolean field, default false).

- [ ] **Step 1: Add the config field**

In `src/main/java/net/vulkanmod/config/Config.java`, add next to `frameQueueSize`:

```java
    public boolean lowLatencyMode = false;
```

- [ ] **Step 2: Override the effective frame queue depth**

In `src/main/java/net/vulkanmod/vulkan/Renderer.java`, find (around line 582):

```java
        int newFramesNum = Initializer.CONFIG.frameQueueSize;
```

Replace with:

```java
        int newFramesNum = Initializer.CONFIG.lowLatencyMode ? 1 : Initializer.CONFIG.frameQueueSize;
```

No other changes needed here — `framesNum` already drives every fence/semaphore/command-buffer array generically (lines 128-199), so a value of 1 is already structurally supported by this file; it just was never reachable via the settings UI before (`frameQueueSize`'s slider is clamped to a 2-5 range).

- [ ] **Step 3: Add the setting next to Frame Queue, gating the slider when active**

In `src/main/java/net/vulkanmod/config/option/Options.java`, find the existing Frame Queue `RangeOption` (around line 475-482):

```java
                        new RangeOption(Component.translatable("vulkanmod.options.frameQueue"),
                                2, 5, 1,
                                value -> {
                                    config.frameQueueSize = value;
                                    Renderer.scheduleSwapChainUpdate();
                                },
                                () -> config.frameQueueSize)
                                .setTooltip(v -> Component.translatable("vulkanmod.options.frameQueue.tooltip")),
```

The Frame Queue option is currently inlined directly inside an `Option<?>[]` array literal (it's in whichever `getXOpts()` method holds it — confirm which by re-reading `Options.java` around line 475 in the live file before editing, since the file may have shifted after Task 4's edits). Pull it out into local variables declared *before* that array literal, in the same method, following the same shape this file already uses for `getPipelineOpts()`'s `throttleRebuildsOption`/`farRebuildBudgetOption` pair (lines 425-448) and `getGraphicsOpts()`'s `texFilteringOption`/`maxAnisotropyOption` pair (lines 202-252) — both gate one option's active state on another's value the same way this needs to:

```java
        var lowLatencyOption = new SwitchOption(Component.translatable("vulkanmod.options.lowLatencyMode"),
                v -> {
                    config.lowLatencyMode = v;
                    Renderer.scheduleSwapChainUpdate();
                },
                () -> config.lowLatencyMode)
                .setTooltip(v -> Component.translatable("vulkanmod.options.lowLatencyMode.tooltip"))
                .setImpact(PerformanceImpact.MEDIUM);

        var frameQueueOption = new RangeOption(Component.translatable("vulkanmod.options.frameQueue"),
                2, 5, 1,
                value -> {
                    config.frameQueueSize = value;
                    Renderer.scheduleSwapChainUpdate();
                },
                () -> config.frameQueueSize)
                .setTooltip(v -> Component.translatable("vulkanmod.options.frameQueue.tooltip"));

        frameQueueOption.setActivationFn(() -> !lowLatencyOption.getNewValue());
        lowLatencyOption.setOnChange(frameQueueOption::updateActiveState);
```

Then, in the `Option<?>[]` array literal where the inlined `RangeOption` used to sit, replace that single element with two consecutive elements: `lowLatencyOption, frameQueueOption` (switch first, so it visually sits above the slider it gates).

- [ ] **Step 4: Add the lang entries**

In `src/main/resources/assets/vulkanmod/lang/en_us.json`, add near the `frameQueue` entries:

```json
  "vulkanmod.options.lowLatencyMode": "Low Latency Mode",
  "vulkanmod.options.lowLatencyMode.tooltip": "Forces the render queue to its minimum depth, reducing input-to-screen delay. Overrides the Render Queue Size setting while on. May cause stutter during heavy CPU frames (chunk rebuild bursts) since there's no longer a queued frame to absorb them. Off by default.",
```

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew.bat compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual runtime verification**

Run: `./gradlew.bat runClient`
In-game: find "Low Latency Mode" next to "Render queue size" in Video settings, confirm enabling it greys out the Render Queue Size slider, confirm no crash on toggling it on/off repeatedly (this triggers a swapchain recreate each time — watch for validation errors if running with `-Dvulkanmod.debug=true` or equivalent validation-layer flag this project uses, if any), and subjectively compare mouse-to-screen feel with it on vs. off. Play for a few minutes with it on and watch for stutter during chunk loading (the known tradeoff) — this is expected, not a bug, but confirm it doesn't crash or corrupt rendering.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/net/vulkanmod/config/Config.java src/main/java/net/vulkanmod/vulkan/Renderer.java src/main/java/net/vulkanmod/config/option/Options.java src/main/resources/assets/vulkanmod/lang/en_us.json
git commit -m "Add Low Latency Mode video setting (forces frame queue depth to 1)"
```
