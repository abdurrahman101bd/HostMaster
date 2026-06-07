package com.ar.hostmaster;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;

public class PowerButtonView extends View {
    private Paint ringPaint, glowPaint, iconPaint, bgPaint;
    private boolean isOn = false;
    private float glowAlpha = 0f;
    private ValueAnimator pulseAnim;
    private OnClickListener externalListener;

    public PowerButtonView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
        setClickable(true);
        setFocusable(true);
        super.setOnClickListener(v -> toggle());
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(8f);
        glowPaint.setMaskFilter(new BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL));

        iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(4f);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void toggle() {
        isOn = !isOn;
        startPulse();
        if (externalListener != null) externalListener.onClick(this);
        invalidate();
    }

    public void setState(boolean on) {
        isOn = on;
        invalidate();
    }

    public boolean isOn() { return isOn; }

    @Override
    public void setOnClickListener(OnClickListener l) { externalListener = l; }

    private void startPulse() {
        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(1f, 0.3f, 1f, 0.3f, 1f);
        pulseAnim.setDuration(600);
        pulseAnim.addUpdateListener(a -> {
            glowAlpha = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r  = Math.min(w, h) / 2f - 20f;

        int offColor = Color.parseColor("#AA3300");
        int onColor  = Color.parseColor("#00FF9D");
        int color    = isOn ? onColor : offColor;

        // Background circle
        bgPaint.setColor(isOn ? Color.parseColor("#0D2B1E") : Color.parseColor("#1A0A0A"));
        canvas.drawCircle(cx, cy, r, bgPaint);

        // Glow ring
        glowPaint.setColor(color);
        glowPaint.setAlpha((int)(255 * glowAlpha * (isOn ? 0.5f : 0.25f)));
        canvas.drawCircle(cx, cy, r, glowPaint);

        // Main ring
        ringPaint.setColor(color);
        canvas.drawCircle(cx, cy, r, ringPaint);

        // Power icon
        iconPaint.setColor(color);
        float iconR = r * 0.38f;

        // Arc (270 degrees, starting from top-left, gap at top)
        RectF arc = new RectF(cx - iconR, cy - iconR, cx + iconR, cy + iconR);
        canvas.drawArc(arc, -220f, 260f, false, iconPaint);

        // Vertical line at top
        canvas.drawLine(cx, cy - iconR * 1.35f, cx, cy - iconR * 0.4f, iconPaint);

        // Status text
        Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        txtPaint.setColor(color);
        txtPaint.setTextSize(r * 0.18f);
        txtPaint.setTextAlign(Paint.Align.CENTER);
        txtPaint.setLetterSpacing(0.15f);
        txtPaint.setTypeface(Typeface.DEFAULT_BOLD);
        String label = isOn ? "ONLINE" : "OFFLINE";
        canvas.drawText(label, cx, cy + r * 0.62f, txtPaint);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int s = Math.min(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs));
        setMeasuredDimension(s, s);
    }
}
