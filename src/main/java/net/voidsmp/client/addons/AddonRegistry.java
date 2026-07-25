package net.voidsmp.client.addons;

import net.voidsmp.client.addons.config.AddonConfigStorage;
import net.voidsmp.client.addons.hud.HudElementManager;
import net.voidsmp.client.addons.hud.HudRenderable;
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

        if (addon instanceof HudRenderable hudRenderable) {
            HudElementManager.register(addon, hudRenderable);
        }

        // TODO: invoke addon.onRegister(...) once the lifecycle hook's contract
        // is finalised. It's protected on Addon (models package) and
        // AddonRegistry lives in a different package and isn't a subclass, so
        // it can't be called from here as-is without widening visibility —
        // out of scope for this task.
    }

    public static List<Addon> all() {
        return Collections.unmodifiableList(ADDONS);
    }
}
