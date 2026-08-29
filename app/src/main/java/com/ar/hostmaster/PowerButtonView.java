package com.ar.hostmaster;

import android.animation.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;

public class PowerButtonView extends View {

    private Paint ringPaint, glowPaint, outerRingPaint, iconPaint, bgPaint, textPaint;
    private boolean isOn = false;
    private float   glowAlpha  = 0f;
    private float   pulseScale = 1f;
    private ValueAnimator pulseAnim, idleAnim;
    private OnClickListener externalListener;

    public PowerButtonView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
        setClickable(true);
        setFocusable(true);
        super.setOnClickListener(v -> toggle());
        startIdlePulse();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(1.5f);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(10f);
        glowPaint.setMaskFilter(new BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL));

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(3.5f);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLetterSpacing(0.18f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private void toggle() {
        isOn = !isOn;
        if (idleAnim != null) { idleAnim.cancel(); idleAnim = null; }
        startClickPulse();
        if (externalListener != null) externalListener.onClick(this);
        invalidate();
    }

    public void setState(boolean on) {
        isOn = on;
        if (idleAnim != null) idleAnim.cancel();
        if (on) {
            startIdlePulse();
        } else {
            glowAlpha = 0.2f;
            pulseScale = 1f;
        }
        invalidate();
    }

    public boolean isOn() { return isOn; }

    @Override
    public void setOnClickListener(OnClickListener l) { externalListener = l; }

    // ── Animations ────────────────────────────────────────────────────────────

    private void startClickPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(1f, 0.2f, 0.8f, 0.2f, 1f);
        pulseAnim.setDuration(500);
        pulseAnim.addUpdateListener(a -> {
            glowAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (isOn) startIdlePulse();
            }
        });
        pulseAnim.start();
    }

    private void startIdlePulse() {
        if (idleAnim != null && idleAnim.isRunning()) return;
        idleAnim = ValueAnimator.ofFloat(0.4f, 1f, 0.4f);
        idleAnim.setDuration(2200);
        idleAnim.setRepeatCount(ValueAnimator.INFINITE);
        idleAnim.setRepeatMode(ValueAnimator.RESTART);
        idleAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        idleAnim.addUpdateListener(a -> {
            glowAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        idleAnim.start();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r  = Math.min(w, h) / 2f - 22f;

        boolean dark = isDarkMode();

        // ── Colors based on theme + state ─────────────────────────────────────
        int onColor, offColor, bgOnColor, bgOffColor, outerColor;

        if (dark) {
            onColor    = Color.parseColor("#00FF9D");
            offColor   = Color.parseColor("#FF4444");
            bgOnColor  = Color.parseColor("#061A10");
            bgOffColor = Color.parseColor("#120606");
            outerColor = Color.parseColor("#1E2D45");
        } else {
            // Light mode - same colors but more visible glow
            onColor    = Color.parseColor("#00BB77");
            offColor   = Color.parseColor("#DD3322");
            bgOnColor  = Color.parseColor("#EAF7F1");
            bgOffColor = Color.parseColor("#FFF0EE");
            outerColor = Color.parseColor("#CCDAEB");
        }

        int activeColor = isOn ? onColor : offColor;

        // ── Outer decorative ring ──────────────────────────────────────────────
        outerRingPaint.setColor(outerColor);
        canvas.drawCircle(cx, cy, r + 10f, outerRingPaint);

        // ── Background circle ─────────────────────────────────────────────────
        bgPaint.setColor(isOn ? bgOnColor : bgOffColor);
        canvas.drawCircle(cx, cy, r, bgPaint);

        // ── Glow ──────────────────────────────────────────────────────────────
        glowPaint.setColor(activeColor);
        float glowIntensity = isOn ? 0.65f : 0.25f;  // Increased for light mode
        glowPaint.setAlpha((int)(255 * glowAlpha * glowIntensity));
        canvas.drawCircle(cx, cy, r, glowPaint);

        // ── Main border ring ──────────────────────────────────────────────────
        ringPaint.setColor(activeColor);
        canvas.drawCircle(cx, cy, r, ringPaint);

        // ── Power icon ────────────────────────────────────────────────────────
        iconPaint.setColor(activeColor);
        float iconR = r * 0.36f;
        RectF arc = new RectF(cx - iconR, cy - iconR, cx + iconR, cy + iconR);
        canvas.drawArc(arc, -220f, 260f, false, iconPaint);
        canvas.drawLine(cx, cy - iconR * 1.38f, cx, cy - iconR * 0.38f, iconPaint);

        // ── Status text ───────────────────────────────────────────────────────
        textPaint.setColor(activeColor);
        textPaint.setTextSize(r * 0.18f);
        canvas.drawText(isOn ? "ONLINE" : "OFFLINE", cx, cy + r * 0.62f, textPaint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isDarkMode() {
        int uiMode = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int s = Math.min(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs));
        setMeasuredDimension(s, s);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnim != null) pulseAnim.cancel();
        if (idleAnim  != null) idleAnim.cancel();
    }
}