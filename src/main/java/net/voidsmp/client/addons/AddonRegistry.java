package net.voidsmp.client.addons;

import net.voidsmp.client.addons.models.Addon;

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
        // TODO: invoke addon.onRegister(...) once the lifecycle hook's contract
        // is finalised (it's protected, and its semantics land with ConfigEntry).
    }

    public static List<Addon> all() {
        return Collections.unmodifiableList(ADDONS);
    }
}
