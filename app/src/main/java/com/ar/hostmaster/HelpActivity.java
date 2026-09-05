package com.ar.hostmaster;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppState state = AppState.get(this);
        ThemeHelper.apply(state.getTheme());
        setContentView(R.layout.activity_help);
        ThemeHelper.applyWithStatusBar(this, state.getTheme());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // GitHub button click
        findViewById(R.id.btn_github).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/abdurrahman101bd/host_master"));
            startActivity(intent);
        });

        // Expand/collapse FAQ items with animation
        setupFaq(R.id.faq_1_header, R.id.faq_1_body);
        setupFaq(R.id.faq_2_header, R.id.faq_2_body);
        setupFaq(R.id.faq_3_header, R.id.faq_3_body);
        setupFaq(R.id.faq_4_header, R.id.faq_4_body);
        setupFaq(R.id.faq_5_header, R.id.faq_5_body);
        
        // Open first FAQ by default
        View firstBody = findViewById(R.id.faq_1_body);
        if (firstBody != null) {
            firstBody.setVisibility(View.VISIBLE);
            View firstHeader = findViewById(R.id.faq_1_header);
            if (firstHeader != null) {
                TextView arrow = firstHeader.findViewWithTag("arrow");
                if (arrow != null) arrow.setRotation(90);
            }
        }
    }

    private void setupFaq(int headerId, int bodyId) {
        View header = findViewById(headerId);
        View body = findViewById(bodyId);
        if (header == null || body == null) return;

        // Initially collapsed (except first one)
        if (bodyId != R.id.faq_1_body) {
            body.setVisibility(View.GONE);
        }

        header.setOnClickListener(v -> {
            boolean isOpen = body.getVisibility() == View.VISIBLE;
            
            // Animate height change
            if (isOpen) {
                collapseView(body);
            } else {
                expandView(body);
            }

            // Rotate arrow
            TextView arrow = header.findViewWithTag("arrow");
            if (arrow != null) {
                float targetRotation = isOpen ? 0 : 90;
                arrow.animate()
                    .rotation(targetRotation)
                    .setDuration(300)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            }
        });
    }

    private void expandView(final View view) {
        view.setVisibility(View.VISIBLE);
        view.measure(View.MeasureSpec.makeMeasureSpec(
            ((View) view.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.UNSPECIFIED);
        
        final int targetHeight = view.getMeasuredHeight();
        view.getLayoutParams().height = 0;
        view.requestLayout();

        ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            view.getLayoutParams().height = (int) animation.getAnimatedValue();
            view.requestLayout();
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
        });
        animator.start();
    }

    private void collapseView(final View view) {
        final int initialHeight = view.getHeight();
        if (initialHeight <= 0) {
            view.setVisibility(View.GONE);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            view.getLayoutParams().height = (int) animation.getAnimatedValue();
            view.requestLayout();
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.requestLayout();
            }
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
        });
        animator.start();
    }
}