package com.xxq.xlai;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PinkSprinkleEffect {

    private static final int PINK_COLOR = Color.parseColor("#FF2D55");
    private static final int PARTICLE_COUNT = 20;
    private static final int DURATION = 600; // ms
    private static final float TRAVEL = 1.8f; // total distance = velocity * TRAVEL

    /**
     * Material views hand out a ContextThemeWrapper, not the Activity itself.
     * Walk down the wrapper chain until the real Activity turns up.
     */
    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void trigger(View targetView) {
        Activity activity = findActivity(targetView.getContext());
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return; // a visual effect must never be able to crash the app
        }

        // Overlay lives on the window's top-level view, so window coordinates match
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();

        int[] location = new int[2];
        targetView.getLocationInWindow(location);
        float centerX = location[0] + targetView.getWidth() / 2f;
        float centerY = location[1] + targetView.getHeight() / 2f;

        List<Particle> particles = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float angle = (float) (Math.PI * 2 * i / PARTICLE_COUNT);
            float velocity = 50 + random.nextFloat() * 100;

            particles.add(new Particle(
                    centerX,
                    centerY,
                    (float) Math.cos(angle) * velocity,
                    (float) Math.sin(angle) * velocity,
                    2 + random.nextFloat() * 4
            ));
        }

        SprinkleOverlay overlay = new SprinkleOverlay(activity, particles);
        decor.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            overlay.updateParticles(progress);
            overlay.invalidate();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (overlay.getParent() != null) {
                    ((ViewGroup) overlay.getParent()).removeView(overlay);
                }
            }
        });

        animator.start();
    }

    private static class Particle {
        final float startX, startY, vx, vy, radius;
        float x, y;
        float alpha = 1f;

        Particle(float x, float y, float vx, float vy, float radius) {
            this.startX = x;
            this.startY = y;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
        }
    }

    private static class SprinkleOverlay extends View {
        private final List<Particle> particles;
        private final Paint paint;

        SprinkleOverlay(Context context, List<Particle> particles) {
            super(context);
            this.particles = particles;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(PINK_COLOR);
        }

        void updateParticles(float progress) {
            for (Particle p : particles) {
                p.x = p.startX + p.vx * progress * TRAVEL;
                p.y = p.startY + p.vy * progress * TRAVEL;
                p.alpha = 1f - progress;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (Particle p : particles) {
                paint.setAlpha((int) (p.alpha * 255));
                canvas.drawCircle(p.x, p.y, p.radius, paint);
            }
        }
    }
}
