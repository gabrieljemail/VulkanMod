package net.voidsmp.client.addons.hud;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One item in a {@link HudRenderable} element's stack — either a run of text
 * or a fixed-size icon. {@link HudElementManager} owns laying these out
 * (order/direction/spacing); an item only describes what to draw, not where.
 */
public sealed interface HudStackItem {
    record Text(Component text, int color) implements HudStackItem {}

    record Icon(Identifier sprite, int size) implements HudStackItem {}
}
