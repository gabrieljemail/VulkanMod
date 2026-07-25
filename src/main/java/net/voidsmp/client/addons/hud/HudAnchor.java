package net.voidsmp.client.addons.hud;

/** The four screen corners a {@link HudRenderable} element can anchor to. */
public enum HudAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public boolean isLeftAnchored() {
        return this == TOP_LEFT || this == BOTTOM_LEFT;
    }

    public boolean isTopAnchored() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }
}
