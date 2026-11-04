package com.bharti.alertalarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Countdown ring on the alarm screen: a dark track with a turquoise arc for the time left. */
public class RingProgressView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress = 1f;

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(10 * d);
        track.setColor(Ui.color(context, R.color.ring_track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(10 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Ui.color(context, R.color.accent));
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(2 * d);
        halo.setColor(0x2E2DD4BF);
    }

    /** 1 = full ring (just started), 0 = empty (time is up). */
    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = Math.min(cx, cy) - halo.getStrokeWidth();
        canvas.drawCircle(cx, cy, outer, halo);

        float r = outer - 20 * d;
        box.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawCircle(cx, cy, r, track);
        if (progress > 0f) {
            canvas.drawArc(box, -90f, 360f * progress, false, arc);
        }
    }
}
