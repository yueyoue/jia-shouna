package com.jiashouna.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import java.util.ArrayList;

/**
 * 全屏图片查看器 - 支持左右滑动和双指缩放
 */
public class ImageViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<String> urls = getIntent().getStringArrayListExtra("image_urls");
        int startPos = getIntent().getIntExtra("position", 0);
        if (urls == null || urls.isEmpty()) {
            finish();
            return;
        }

        // 构建UI
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(40), dp(16), dp(12));

        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextSize(20);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnClose.setOnClickListener(v -> finish());
        topBar.addView(btnClose);

        TextView tvCounter = new TextView(this);
        tvCounter.setText((startPos + 1) + " / " + urls.size());
        tvCounter.setTextSize(16);
        tvCounter.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams counterLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        counterLp.setMarginStart(dp(16));
        tvCounter.setLayoutParams(counterLp);
        topBar.addView(tvCounter);

        root.addView(topBar);

        // 图片ViewPager
        ViewPager2 viewPager = new ViewPager2(this);
        viewPager.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        viewPager.setAdapter(new ImagePagerAdapter(urls));
        viewPager.setCurrentItem(startPos, false);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tvCounter.setText((position + 1) + " / " + urls.size());
            }
        });
        root.addView(viewPager);

        setContentView(root);
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class ImagePagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.VH> {
        private final ArrayList<String> urls;
        ImagePagerAdapter(ArrayList<String> urls) { this.urls = urls; }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            // 使用支持缩放的ImageView
            android.widget.FrameLayout container = new android.widget.FrameLayout(parent.getContext());
            container.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setClickable(true);
            iv.setFocusable(true);
            container.addView(iv);

            return new VH(container, iv);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Glide.with(holder.iv.getContext())
                .load(urls.get(position))
                .into(holder.iv);

            // 双指缩放支持
            holder.iv.setOnTouchListener(new ZoomTouchListener(holder.iv));
        }

        @Override
        public int getItemCount() { return urls.size(); }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView iv;
            VH(android.view.View v, ImageView iv) {
                super(v);
                this.iv = iv;
            }
        }
    }

    /**
     * 双指缩放 + 单指拖动触摸监听器
     */
    private static class ZoomTouchListener implements android.view.View.OnTouchListener {
        private final ImageView imageView;
        private float startDistance = 0;
        private float startScale = 1f;
        private float[] lastTouch = new float[2];
        private boolean isDragging = false;

        ZoomTouchListener(ImageView iv) { this.imageView = iv; }

        @Override
        public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    lastTouch[0] = event.getX();
                    lastTouch[1] = event.getY();
                    isDragging = false;
                    return true;

                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() >= 2) {
                        startDistance = getDistance(event);
                        startScale = getCurrentScale();
                    }
                    return true;

                case android.view.MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() >= 2) {
                        // 双指缩放
                        float newDist = getDistance(event);
                        float scale = startScale * (newDist / startDistance);
                        scale = Math.max(0.5f, Math.min(scale, 5f));
                        imageView.setScaleX(scale);
                        imageView.setScaleY(scale);
                    } else if (event.getPointerCount() == 1) {
                        // 单指拖动
                        float dx = event.getX() - lastTouch[0];
                        float dy = event.getY() - lastTouch[1];
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isDragging = true;
                            imageView.setTranslationX(imageView.getTranslationX() + dx);
                            imageView.setTranslationY(imageView.getTranslationY() + dy);
                            lastTouch[0] = event.getX();
                            lastTouch[1] = event.getY();
                        }
                    }
                    return true;

                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!isDragging && event.getPointerCount() == 1) {
                        // 单击 - 不做处理，由外部ViewPager处理
                    }
                    return true;

                case android.view.MotionEvent.ACTION_POINTER_UP:
                    // 双指抬起时重置
                    startScale = getCurrentScale();
                    return true;
            }
            return false;
        }

        private float getDistance(android.view.MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float getCurrentScale() {
            return imageView.getScaleX();
        }
    }
}
