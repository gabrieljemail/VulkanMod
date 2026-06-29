package net.voidsmp.client;

/*

+-------------------------------------+
|       Welcome to Void Client!       |
+-------------------------------------+

If you're new here, the codebase is pretty well organized, so you should be fine.
To be perfectly clear, Void Client is a highly optimized Minecraft mod built around
VulkanMod, but with a ton of extra optimizations and some cool features, all while
being minimalistic.

** DO NOT MIX WITH OTHER CLIENTS! **
Void Client has deep modifications that WILL break other mods if you don't know what
you're doing. Unless the mod is listed under [Supported Mods] or you are a modder,
assume it will break things.

--- MODDING GUIDE ----------------
The first rule of modding Void Client is to remember **modularity**. The mod uses a
system of modules to organize its mixins and hooks. Each module is called an add-on.

To register an add-on, simply extend the VCAddon class in your add-on's main class.
You will have to provide the following information via static fields, being:
    - "id":         The package name of your add-on. You should use your classpath in almost
                    all cases for clarity.
    - "name":       The friendly name of the add-on. Displayed in the ClickGUI as the title.
    - "lifeGoals":  A brief description of what the add-on does. This is purely for
                    user experience.
    - "config":     An object-based configuration system.

--- SUPPORTED MODS ---------------
Because of Void Client's deep modifications, it is recommended to assume that most optimization,
cosmetic, and cheating mods will not work. Here is a list of supported mods that will work in
almost all cases:
    - Beryl
    - Armor HUD mods
    - Totem counters
    - Smooth scrolling/GUI
    - Integrated server mods (e.g. Lithium)
    - Krypton (not other networking mods!)
    - OptiGUI

Here's a list of mods that will work, but will be redundant and might reduce performance gains:
    - Culling mods (e.g. Entity Culling by tr7zw)
    - Vape V4 (Vape Lite is fine)
    - Baritone
    - Super Fast Math/FabricFastMath

Here's a list of mods that will NOT work and may break your game:
    - Sodium/Embeddium
    - Iris, or any shader mod that isn't Beryl
    - OptiFine and any port of its features
    - Continuity
    - Distant Horizons
    - Voxy
    - Meteor Client
    - LiquidBounce
    - Mods using MCEF or JCEF
    - Essential
    - Anti-aliasing mods
    - Super Resolution

If you just scrolled past all of that, I don't blame you. But be careful before you break the mod.

*/

import net.fabricmc.api.ClientModInitializer;
import net.voidsmp.client.addons.AddonRegistry;
import net.voidsmp.client.addons.Fullbright;
import net.voidsmp.client.addons.TestAddon;
import net.voidsmp.client.clickgui.ClickGUI;

public class VoidClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Constructing the ClickGUI registers its keybinding and tick handler.
        new ClickGUI();

        // Register add-ons. (TestAddon is temporary until real ones land.)
        AddonRegistry.register(new TestAddon());
        AddonRegistry.register(new Fullbright());
    }
}