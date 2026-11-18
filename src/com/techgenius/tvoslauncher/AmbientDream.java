package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.dreams.DreamService;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Ambient screensaver, shown by the system when the TV goes idle.
 *
 * This is a native port of Veil's ambient dream (upstream is Kotlin + Compose, which this
 * build cannot compile — see build.sh), so the composition is redrawn with Canvas instead:
 * radial #232A36 -> #15171C wash, "JAGAN" + lock top-left, clock and temperature top-right,
 * and the "PRIVATE BY DESIGN" block. Deliberately no network of its own: temperature comes
 * from {@link Weather#cached}, which the launcher already refreshes.
 */
public class AmbientDream extends DreamService {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new AmbientView(this));
    }

    /** The whole ambient surface. */
    static final class AmbientView extends View {

        private static final int BG_DARK = 0xFF15171C;
        private static final int BG_GLOW = 0xFF232A36;
        private static final int TEXT = 0xFFF4F5F7;
        private static final int ACCENT = 0xFFD9FF6B;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path lock = new Path();
        private final RectF lockBody = new RectF();
        private final SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.getDefault());
        private final SimpleDateFormat ampm = new SimpleDateFormat("a", Locale.getDefault());
        private final Handler handler = new Handler(Looper.getMainLooper());

        private Typeface bold, regular;
        private int temp = Integer.MIN_VALUE;

        /** Burn-in guard: the whole composition drifts a few dp instead of sitting still. */
        private static final float DRIFT_DP = 6f;
        private static final long DRIFT_MS = 60000L;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                invalidate();
                handler.postDelayed(this, 15000L);
            }
        };

        AmbientView(Context c) {
            super(c);
            setBackgroundColor(BG_DARK);
            bold = Ui.font(c, "semibold");
            regular = Ui.font(c, "regular");
            loadTemp(c);
        }

        private void loadTemp(Context c) {
            try {
                Weather.Data d = Weather.cached(c);
                if (d != null) temp = d.temp;
            } catch (Exception ignored) {
            }
        }

        private float sp(float v) {
            return v * getResources().getDisplayMetrics().scaledDensity;
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            handler.post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();

            // Radial wash, brightest above centre like the original.
            p.setShader(new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.75f,
                    new int[]{BG_GLOW, BG_DARK}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // Gentle bottom fall-off so text never fights the background.
            p.setShader(new LinearGradient(0, h * 0.55f, 0, h,
                    new int[]{Color.TRANSPARENT, 0xB0000000}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, h * 0.55f, w, h, p);
            p.setShader(null);

            float t = (SystemClock.uptimeMillis() % DRIFT_MS) / (float) DRIFT_MS;
            double ang = t * Math.PI * 2;
            float dx = (float) Math.cos(ang) * dp(DRIFT_DP);
            float dy = (float) Math.sin(ang) * dp(DRIFT_DP);
            cv.save();
            cv.translate(dx, dy);

            float pad = dp(48);
            drawWordmark(cv, pad, dp(30));
            drawClockAndTemp(cv, w - pad, dp(24));
            drawPrivate(cv, pad, h * 0.5f);

            cv.restore();
        }

        /** Top-left: padlock + "JAGAN". */
        private void drawWordmark(Canvas cv, float x, float top) {
            float s = dp(12);
            float y = top + sp(14) * 0.78f;
            // shackle
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.6f));
            p.setColor(withAlpha(TEXT, 0.70f));
            float cx = x + s * 0.5f;
            cv.drawArc(cx - s * 0.3f, y - s * 1.05f, cx + s * 0.3f, y - s * 0.15f, 180, 180, false, p);
            // body
            p.setStyle(Paint.Style.FILL);
            lockBody.set(x, y - s * 0.25f, x + s, y + s * 0.72f);
            cv.drawRoundRect(lockBody, dp(1.5f), dp(1.5f), p);

            p.setTypeface(bold);
            p.setTextSize(sp(14));
            p.setColor(withAlpha(TEXT, 0.70f));
            p.setLetterSpacing(0.10f);
            cv.drawText("JAGAN", x + s + dp(6), y + sp(14) * 0.28f, p);
            p.setLetterSpacing(0f);
        }

        /** Top-right: clock, then temperature if the launcher has a cached forecast. */
        private void drawClockAndTemp(Canvas cv, float right, float top) {
            Date now = Calendar.getInstance().getTime();
            String timePart = hm.format(now);
            String amPm = ampm.format(now);
            String tempPart = temp == Integer.MIN_VALUE ? null : (temp + "\u00B0");

            p.setTypeface(bold);
            p.setTextSize(sp(30));
            p.setColor(withAlpha(TEXT, 0.92f));
            float timeW = p.measureText(timePart);

            p.setTextSize(sp(13));
            float amW = p.measureText(amPm);

            float tempW = 0;
            if (tempPart != null) {
                p.setTextSize(sp(24));
                p.setColor(withAlpha(TEXT, 0.90f));
                tempW = p.measureText(tempPart) + dp(14);
            }

            float total = timeW + dp(6) + amW + tempW;
            float x = right - total;

            p.setTypeface(bold);
            p.setTextSize(sp(30));
            p.setColor(withAlpha(TEXT, 0.92f));
            float baseline = top + sp(30) * 0.82f;
            cv.drawText(timePart, x, baseline, p);

            p.setTextSize(sp(13));
            p.setColor(withAlpha(TEXT, 0.60f));
            cv.drawText(amPm, x + timeW + dp(6), baseline - sp(3), p);

            if (tempPart != null) {
                p.setTextSize(sp(24));
                p.setColor(withAlpha(TEXT, 0.90f));
                cv.drawText(tempPart, right - p.measureText(tempPart), baseline, p);
            }
        }

        /** The quiet centre-left block lifted from Veil's idle face. */
        private void drawPrivate(Canvas cv, float x, float cy) {
            p.setTypeface(bold);
            p.setTextSize(sp(10));
            p.setColor(ACCENT);
            p.setLetterSpacing(0.14f);
            cv.drawText("PRIVATE BY DESIGN", x, cy - sp(26), p);
            p.setLetterSpacing(0f);

            p.setTypeface(bold);
            p.setTextSize(sp(34));
            p.setColor(TEXT);
            p.setLetterSpacing(-0.03f);
            cv.drawText("Private by design.", x, cy + sp(8), p);
            p.setLetterSpacing(0f);
        }

        private static int withAlpha(int color, float a) {
            return (color & 0x00FFFFFF) | (Math.round(a * 255f) << 24);
        }
    }
}
