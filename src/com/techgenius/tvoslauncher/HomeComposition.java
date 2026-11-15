package com.techgenius.tvoslauncher;

/**
 * Pure (Android-free) layout metrics and policy for the editorial Home composition.
 * All design values are in 960x540 units and scale by u = min(w/960, h/540).
 */
final class HomeComposition {
    static final String PREF_KEY = "home_editorial";
    static final float FOCUS_SCALE = 1.08f;
    static final float FOCUS_LIFT_U = 4f;
    static final int MAX_SHELF_CARDS = 3;

    static final float LEFT_U = 40f;
    static final float SAFE_INSET_U = 24f;
    static final float GREET_BASELINE_U = 75f;
    static final float CLOCK_PX_U = 84f;
    static final float CLOCK_TOP_U = 92f;
    static final float CLOCK_CAP_RATIO = 0.70f;
    static final float DATE_GAP_U = 30f;
    static final float SHELF_HEADER_TOP_U = 214f;
    static final float SHELF_TOP_U = 236f;
    static final float CARD_W_U = 282f;
    static final float CARD_H_U = 88f;
    static final float CARD_GAP_U = 12f;
    static final float DOCK_CLEARANCE_U = 24f;
    static final float TOOLTIP_BAND_U = 35f;
    static final float DOCK_PAD_V_U = 10f;
    static final float TOP_BAR_TOP_U = 28f;
    static final float TOP_BAR_RIGHT_U = 40f;

    static final class Metrics {
        float u, left, safeInset, greetBaseline, clockTextPx, clockBaseline, dateBaseline;
        float shelfHeaderTop, shelfTop, shelfBottom, shelfCardW, shelfCardH, shelfGap;
        float dockBottomClearance, dockBottom, dockTop, tooltipTop;
        float topBarTop, topBarRight;
    }

    private HomeComposition() { }

    static Metrics compute(int w, int h, int dockRows, float dockScale) {
        Metrics m = new Metrics();
        float u = Math.min(w / 960f, h / 540f);
        m.u = u;
        m.left = LEFT_U * u;
        m.safeInset = SAFE_INSET_U * u;
        m.greetBaseline = GREET_BASELINE_U * u;
        m.clockTextPx = CLOCK_PX_U * u;
        m.clockBaseline = (CLOCK_TOP_U + CLOCK_PX_U * CLOCK_CAP_RATIO) * u;
        m.dateBaseline = m.clockBaseline + DATE_GAP_U * u;
        m.shelfHeaderTop = SHELF_HEADER_TOP_U * u;
        m.shelfTop = SHELF_TOP_U * u;
        m.shelfCardW = CARD_W_U * u;
        m.shelfCardH = CARD_H_U * u;
        m.shelfGap = CARD_GAP_U * u;
        m.shelfBottom = m.shelfTop + m.shelfCardH;
        m.dockBottomClearance = DOCK_CLEARANCE_U * u;
        m.dockBottom = h - m.dockBottomClearance;
        int rows = Math.max(1, dockRows);
        float tile = Math.max(26f, Math.round(54f * dockScale));
        float gap = Math.max(4f, Math.round(10f * dockScale));
        float padV = DOCK_PAD_V_U; // SceneHome uses a fixed dp(10) pad; it does not scale with dock scale
        m.dockTop = m.dockBottom - (rows * tile + (rows - 1) * gap + 2 * padV) * u;
        m.tooltipTop = m.dockTop - TOOLTIP_BAND_U * u;
        m.topBarTop = TOP_BAR_TOP_U * u;
        m.topBarRight = w - TOP_BAR_RIGHT_U * u;
        return m;
    }

    /** Matches the existing Scenes formatTime patterns: "h:mm" (12h) and "HH:mm" (24h). */
    static String clockText(int hour24, int minute, boolean is24h) {
        int h = ((hour24 % 24) + 24) % 24;
        int mm = ((minute % 60) + 60) % 60;
        int shown = is24h ? h : (h % 12 == 0 ? 12 : h % 12);
        return (is24h && shown < 10 ? "0" : "") + shown + ":" + (mm < 10 ? "0" : "") + mm;
    }

    /** Date plus real weather only; never fabricates a temperature. */
    static String infoLine(String date, String temp, String desc) {
        if (temp == null || temp.isEmpty()) return date;
        String d = (desc == null || desc.isEmpty()) ? "" : " " + desc;
        return date + "   ·   " + temp + d;
    }

    static int shelfCount(int n) {
        return Math.max(0, Math.min(MAX_SHELF_CARDS, n));
    }

    static boolean progressVisible(float progress) {
        return progress >= 0f;
    }

    static int progressFillPx(int trackPx, float progress) {
        return Math.round(Math.max(0, trackPx) * Math.max(0f, Math.min(1f, progress)));
    }

    /** Opt-out preference: absent means the new editorial home is on. */
    static boolean editorialEnabled(Boolean stored) {
        return stored == null || stored;
    }

    /** True when the style bound at creation differs from the current pref, so Home must be rebuilt. */
    static boolean styleChanged(boolean bound, boolean current) {
        return bound != current;
    }
}
