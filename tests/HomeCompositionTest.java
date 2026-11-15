package com.techgenius.tvoslauncher;

/** Behavioral checks for the pure editorial-home layout/policy calculations. */
public class HomeCompositionTest {
    static int checks;
    static void ok(boolean b, String what) {
        checks++;
        if (!b) throw new AssertionError("check " + checks + " failed: " + what);
    }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.01f * Math.max(1f, Math.abs(b)); }

    static void checkSize(int w, int h) {
        for (int rows = 1; rows <= 2; rows++) {
            HomeComposition.Metrics m = HomeComposition.compute(w, h, rows, 1f);
            float u = Math.min(w / 960f, h / 540f);
            String t = w + "x" + h + " rows=" + rows + ": ";
            ok(near(m.u, u), t + "unit");
            ok(near(m.left, 40 * u), t + "masthead left 40u");
            ok(near(m.greetBaseline, 75 * u), t + "greeting baseline 75u");
            ok(m.clockTextPx / u >= 76f - 0.01f && m.clockTextPx / u <= 88f + 0.01f, t + "clock 76-88u");
            ok(m.clockBaseline > m.greetBaseline + 0.1f * m.clockTextPx, t + "clock below greeting");
            ok(m.dateBaseline > m.clockBaseline, t + "date below clock");
            ok(m.shelfHeaderTop > m.dateBaseline, t + "shelf below hero");
            ok(near(m.shelfCardW, 282 * u) && near(m.shelfCardH, 88 * u), t + "card 282x88u");
            ok(near(m.dockBottomClearance, 24 * u), t + "dock clearance 24u");
            ok(near(h - m.dockBottomClearance, m.dockBottom), t + "dock bottom");
            ok(m.dockTop < m.dockBottom, t + "dock height positive");
            ok(m.tooltipTop < m.dockTop, t + "tooltip above dock");
            // Shelf (including focus lift/scale) must clear dock tooltips.
            float focusedBottom = m.shelfTop + m.shelfCardH + m.shelfCardH * (HomeComposition.FOCUS_SCALE - 1f) / 2f;
            ok(focusedBottom <= m.tooltipTop - 8 * u + 0.01f, t + "shelf clears tooltip band");
            ok(m.shelfBottom <= m.tooltipTop, t + "shelf bottom clears tooltip");
            // Three cards: focus-scaled outer bounds stay inside the safe inset.
            float rowW = 3 * m.shelfCardW + 2 * m.shelfGap;
            float grow = m.shelfCardW * (HomeComposition.FOCUS_SCALE - 1f) / 2f;
            ok(m.left - grow >= m.safeInset, t + "focused left edge safe");
            ok(m.left + rowW + grow <= w - m.safeInset, t + "focused right edge safe");
            ok(m.shelfTop - HomeComposition.FOCUS_LIFT_U * u - m.shelfCardH * (HomeComposition.FOCUS_SCALE - 1f) / 2f
                    > m.shelfHeaderTop, t + "focused top does not hit header");
            ok(m.dockBottom <= h - m.safeInset + 0.01f, t + "dock in safe area");
            ok(m.topBarTop >= m.safeInset - 0.01f, t + "top controls inside safe area");
            ok(w - m.topBarRight <= w - m.safeInset + 0.01f, t + "top controls right safe");
        }
        // Linear scaling: metrics at this size equal 960x540 metrics times u.
        HomeComposition.Metrics base = HomeComposition.compute(960, 540, 1, 1f);
        HomeComposition.Metrics m = HomeComposition.compute(w, h, 1, 1f);
        float u = Math.min(w / 960f, h / 540f);
        ok(near(m.clockBaseline, base.clockBaseline * u), "clock baseline scales " + w);
        ok(near(m.shelfTop, base.shelfTop * u), "shelf top scales " + w);
    }

    public static void main(String[] args) {
        checkSize(960, 540);
        checkSize(1280, 720);
        checkSize(1920, 1080);

        // Bigger dock scale / second row never overlaps shelf's hero area.
        HomeComposition.Metrics big = HomeComposition.compute(1920, 1080, 2, 1.2f);
        ok(big.dockTop > big.dateBaseline, "tall dock stays below hero");

        // Clock honours 12/24h, no leading zero in 12h (matches existing "h:mm" / "HH:mm").
        ok("12:05".equals(HomeComposition.clockText(0, 5, false)), "00:05 12h");
        ok("1:07".equals(HomeComposition.clockText(13, 7, false)), "13:07 12h");
        ok("12:30".equals(HomeComposition.clockText(12, 30, false)), "noon 12h");
        ok("13:07".equals(HomeComposition.clockText(13, 7, true)), "13:07 24h");
        ok("00:05".equals(HomeComposition.clockText(0, 5, true)), "00:05 24h");

        // Info line shows only real data.
        ok("Sat, Nov 15".equals(HomeComposition.infoLine("Sat, Nov 15", null, null)), "no weather");
        ok("Sat, Nov 15".equals(HomeComposition.infoLine("Sat, Nov 15", "", "Clear")), "empty temp");
        ok("Sat, Nov 15   ·   21° Clear".equals(HomeComposition.infoLine("Sat, Nov 15", "21°", "Clear")), "temp+desc");
        ok("Sat, Nov 15   ·   21°".equals(HomeComposition.infoLine("Sat, Nov 15", "21°", null)), "temp only");

        // Shelf count: max 3, never negative, zero stays zero (no fake cards).
        ok(HomeComposition.shelfCount(0) == 0, "no cards");
        ok(HomeComposition.shelfCount(-4) == 0, "negative");
        ok(HomeComposition.shelfCount(2) == 2, "two");
        ok(HomeComposition.shelfCount(9) == 3, "max three");

        // Progress is clamped, and hidden (negative) when unknown.
        ok(HomeComposition.progressFillPx(200, 0.5f) == 100, "half");
        ok(HomeComposition.progressFillPx(200, 2f) == 200, "clamp hi");
        ok(HomeComposition.progressFillPx(200, -1f) == 0, "unknown -> 0");
        ok(!HomeComposition.progressVisible(-1f), "unknown hidden");
        ok(HomeComposition.progressVisible(0f), "zero shown");

        // Opt-out preference: default true when never stored, explicit false falls back.
        ok(HomeComposition.editorialEnabled(null), "default on");
        ok(HomeComposition.editorialEnabled(Boolean.TRUE), "on");
        ok(!HomeComposition.editorialEnabled(Boolean.FALSE), "off");
        ok("home_editorial".equals(HomeComposition.PREF_KEY), "pref key");

        // Style change detection drives MainActivity recreate on resume.
        ok(!HomeComposition.styleChanged(true, true), "editorial unchanged");
        ok(!HomeComposition.styleChanged(false, false), "classic unchanged");
        ok(HomeComposition.styleChanged(true, false), "editorial -> classic");
        ok(HomeComposition.styleChanged(false, true), "classic -> editorial");

        // Dock padding is the fixed 10u of SceneHome (padV = dp(10)); it never scales with dock scale.
        for (int[] sz : new int[][]{{960, 540}, {1280, 720}, {1920, 1080}}) {
            float u = Math.min(sz[0] / 960f, sz[1] / 540f);
            for (float sc : new float[]{0.5f, 1f, 1.5f}) {
                HomeComposition.Metrics d = HomeComposition.compute(sz[0], sz[1], 1, sc);
                float tile = Math.max(26f, Math.round(54f * sc));
                float expectH = (tile + 2 * 10f) * u;
                String t = sz[0] + "x" + sz[1] + " scale=" + sc + ": ";
                ok(near(d.dockBottom - d.dockTop, expectH), t + "single-row dock height uses fixed 10u padding");
                float focusedBottom = d.shelfTop + d.shelfCardH + d.shelfCardH * (HomeComposition.FOCUS_SCALE - 1f) / 2f;
                ok(focusedBottom <= d.tooltipTop + 0.01f, t + "single-row dock clears shelf");
            }
            // Two rows at 1x: 2 tiles + 1 gap + fixed padding.
            HomeComposition.Metrics two = HomeComposition.compute(sz[0], sz[1], 2, 1f);
            ok(near(two.dockBottom - two.dockTop, (2 * 54f + 10f + 20f) * u), "two-row 1x dock height");
        }

        System.out.println("PASS " + checks + " HomeComposition behavioral checks");
    }
}
