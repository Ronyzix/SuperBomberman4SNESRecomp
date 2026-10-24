package com.manus.sbomber4;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

final class TouchControls extends View {
    private static final int A = 0;
    private static final int B = 1;
    private static final int X = 2;
    private static final int Y = 3;
    private static final int L = 4;
    private static final int R = 5;
    private static final int START = 6;
    private static final int SELECT = 7;
    private static final int UP = 8;
    private static final int DOWN = 9;
    private static final int LEFT = 10;
    private static final int RIGHT = 11;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Set<Integer>> pointerButtons = new SparseArray<>();
    private final Set<Integer> pressedButtons = new HashSet<>();

    TouchControls(Context context) {
        super(context);
        setClickable(true);
        setFocusable(false);
        setContentDescription("Controles virtuais multitoque do Super Nintendo");
        fill.setStyle(Paint.Style.FILL);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(1.5f));
        outline.setColor(Color.argb(190, 244, 241, 234));
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawDpad(canvas, width, height);

        float faceRadius = height * 0.070f;
        float faceX = width * 0.84f;
        float faceY = height * 0.71f;
        float offset = faceRadius * 1.55f;
        drawButton(canvas, faceX + offset, faceY, faceRadius, "A", A);
        drawButton(canvas, faceX, faceY + offset, faceRadius, "B", B);
        drawButton(canvas, faceX, faceY - offset, faceRadius, "X", X);
        drawButton(canvas, faceX - offset, faceY, faceRadius, "Y", Y);

        float shoulderRadius = height * 0.064f;
        drawButton(canvas, width * 0.16f, height * 0.17f, shoulderRadius, "L", L);
        drawButton(canvas, width * 0.84f, height * 0.17f, shoulderRadius, "R", R);
        drawButton(canvas, width * 0.455f, height * 0.90f, height * 0.047f, "SELECT", SELECT);
        drawButton(canvas, width * 0.545f, height * 0.90f, height * 0.047f, "START", START);
    }

    private void drawDpad(Canvas canvas, float width, float height) {
        float cx = width * 0.16f;
        float cy = height * 0.72f;
        float radius = height * 0.17f;
        fill.setColor(Color.argb(92, 10, 17, 27));
        canvas.drawCircle(cx, cy, radius, fill);
        fill.setColor(Color.argb(142, 10, 17, 27));
        float arm = radius * 0.34f;
        canvas.drawRoundRect(new RectF(cx - radius * 0.90f, cy - arm,
                cx + radius * 0.90f, cy + arm), arm, arm, fill);
        canvas.drawRoundRect(new RectF(cx - arm, cy - radius * 0.90f,
                cx + arm, cy + radius * 0.90f), arm, arm, fill);
        canvas.drawCircle(cx, cy, radius * 0.21f, outline);
        drawArrow(canvas, cx, cy - radius * 0.53f, "▲", UP);
        drawArrow(canvas, cx, cy + radius * 0.66f, "▼", DOWN);
        drawArrow(canvas, cx - radius * 0.57f, cy + radius * 0.08f, "◀", LEFT);
        drawArrow(canvas, cx + radius * 0.57f, cy + radius * 0.08f, "▶", RIGHT);
        canvas.drawCircle(cx, cy, radius, outline);
    }

    private void drawArrow(Canvas canvas, float cx, float cy, String label, int button) {
        text.setTextSize(getHeight() * 0.043f);
        text.setAlpha(pressedButtons.contains(button) ? 255 : 205);
        canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2.0f, text);
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label, int button) {
        fill.setColor(pressedButtons.contains(button)
                ? Color.argb(205, 250, 187, 73)
                : Color.argb(112, 16, 25, 38));
        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius, outline);
        text.setAlpha(238);
        text.setTextSize(label.length() > 3 ? radius * 0.50f : radius * 0.92f);
        canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2.0f, text);
    }

    private Set<Integer> buttonsAt(float x, float y) {
        HashSet<Integer> hits = new HashSet<>();
        float width = getWidth();
        float height = getHeight();

        float dpadX = width * 0.16f;
        float dpadY = height * 0.72f;
        float dpadRadius = height * 0.17f;
        float dx = x - dpadX;
        float dy = y - dpadY;
        if (dx * dx + dy * dy <= dpadRadius * dpadRadius) {
            float threshold = dpadRadius * 0.20f;
            if (dx < -threshold) hits.add(LEFT);
            if (dx > threshold) hits.add(RIGHT);
            if (dy < -threshold) hits.add(UP);
            if (dy > threshold) hits.add(DOWN);
        }

        float faceRadius = height * 0.070f;
        float faceX = width * 0.84f;
        float faceY = height * 0.71f;
        float offset = faceRadius * 1.55f;
        hitCircle(hits, x, y, faceX + offset, faceY, faceRadius * 1.25f, A);
        hitCircle(hits, x, y, faceX, faceY + offset, faceRadius * 1.25f, B);
        hitCircle(hits, x, y, faceX, faceY - offset, faceRadius * 1.25f, X);
        hitCircle(hits, x, y, faceX - offset, faceY, faceRadius * 1.25f, Y);

        float shoulderRadius = height * 0.064f;
        hitCircle(hits, x, y, width * 0.16f, height * 0.17f, shoulderRadius * 1.45f, L);
        hitCircle(hits, x, y, width * 0.84f, height * 0.17f, shoulderRadius * 1.45f, R);
        hitCircle(hits, x, y, width * 0.455f, height * 0.90f, height * 0.060f, SELECT);
        hitCircle(hits, x, y, width * 0.545f, height * 0.90f, height * 0.060f, START);
        return hits;
    }

    private void hitCircle(Set<Integer> result, float x, float y,
                           float cx, float cy, float radius, int button) {
        float dx = x - cx;
        float dy = y - cy;
        if (dx * dx + dy * dy <= radius * radius) {
            result.add(button);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                updatePointer(event, index);
                updatePressedButtons();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pointerId = event.getPointerId(i);
                    if (pointerButtons.get(pointerId) != null) {
                        updatePointer(event, i);
                    }
                }
                updatePressedButtons();
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                pointerButtons.remove(event.getPointerId(index));
                updatePressedButtons();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pointerButtons.clear();
                updatePressedButtons();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void updatePointer(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        pointerButtons.put(pointerId, buttonsAt(event.getX(index), event.getY(index)));
    }

    private void updatePressedButtons() {
        HashSet<Integer> next = new HashSet<>();
        for (int i = 0; i < pointerButtons.size(); i++) {
            Set<Integer> current = pointerButtons.valueAt(i);
            if (current != null) {
                next.addAll(current);
            }
        }
        for (int button = A; button <= RIGHT; button++) {
            boolean wasPressed = pressedButtons.contains(button);
            boolean isPressed = next.contains(button);
            if (wasPressed != isPressed) {
                MainActivity.nativePadButton(button, isPressed);
            }
        }
        pressedButtons.clear();
        pressedButtons.addAll(next);
    }

    @Override
    protected void onDetachedFromWindow() {
        pointerButtons.clear();
        updatePressedButtons();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
