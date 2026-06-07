package com.ar.hostmaster;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayDeque;
import java.util.Deque;

public class ChartView extends View {
    private static final int POINTS = 40;
    private final Deque<Float> data = new ArrayDeque<>();
    private Paint linePaint, fillPaint, gridPaint;
    private Path linePath, fillPath;
    private float maxVal = 1f;

    public ChartView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.5f);
        linePaint.setColor(Color.parseColor("#00D4FF"));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new LinearGradient(0, 0, 0, 1,
                Color.parseColor("#4400D4FF"), Color.TRANSPARENT, Shader.TileMode.CLAMP));

        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#1E2D45"));
        gridPaint.setStrokeWidth(1f);

        linePath = new Path();
        fillPath = new Path();
        for (int i = 0; i < POINTS; i++) data.addLast(0f);
    }

    public void addPoint(float value) {
        data.removeFirst();
        data.addLast(value);
        if (value > maxVal) maxVal = value;
        // Slowly decay max
        maxVal = Math.max(1f, maxVal * 0.995f);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#5500D4FF"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        // Grid lines
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        Float[] pts = data.toArray(new Float[0]);
        float stepX = w / (float)(POINTS - 1);

        linePath.reset();
        fillPath.reset();
        fillPath.moveTo(0, h);

        for (int i = 0; i < POINTS; i++) {
            float x = i * stepX;
            float y = h - (pts[i] / maxVal) * (h - 4);
            if (i == 0) { linePath.moveTo(x, y); fillPath.lineTo(x, y); }
            else { linePath.lineTo(x, y); fillPath.lineTo(x, y); }
        }
        fillPath.lineTo(w, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}
