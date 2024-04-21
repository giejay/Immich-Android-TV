package nl.giejay.android.tv.immich.playback;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import nl.giejay.android.tv.immich.R;
import timber.log.Timber;

public class ScreenSaverSliderView extends ConstraintLayout implements ViewPager.OnPageChangeListener {
    private Handler mainHandler;
    private ViewPager mPager;
    private final Runnable goToNextAssetRunnable = this::goToNextAsset;
    private List<ScreenSaverItem> items;
    private ScreenSaverPagerAdapter pagerAdapter;
    private TextView timeTextView;
    private TextView subtitleTextView;
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat sdf = new SimpleDateFormat("h:mm");
    private long automatedPagingIntervalMs = 1000L;

    public ScreenSaverSliderView(@NonNull Context context) {
        this(context, null);
    }

    public ScreenSaverSliderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScreenSaverSliderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }


    public ScreenSaverSliderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.screensaver, this);
        timeTextView = findViewById(R.id.time);
        subtitleTextView = findViewById(R.id.album_info);
        onTimeChanged();
        mainHandler = new Handler(Looper.getMainLooper());
        mPager = findViewById(R.id.pager);
        pagerAdapter = new ScreenSaverPagerAdapter(getContext(), items);
        items = new ArrayList<>();
        pagerAdapter.setItems(items);
        mPager.setAdapter(pagerAdapter);
        mPager.setCurrentItem(0);
        mPager.setOffscreenPageLimit(2);
        mPager.addOnPageChangeListener(this);
    }

    public void onTimeChanged() {
        timeTextView.setText(sdf.format(new Date()));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPager == null || mPager.getAdapter() == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT && mPager.getAdapter().getCount() - 1 == mPager.getCurrentItem()) {
                // last item, go to first
                mPager.setCurrentItem(0, false);
                return false;
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT && 0 == mPager.getCurrentItem()) {
                // last item, go to first
                mPager.setCurrentItem(mPager.getAdapter().getCount() - 1, false);
                return false;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void startTimerNextAsset() {
        mainHandler.removeCallbacks(goToNextAssetRunnable);
        mainHandler.postDelayed(goToNextAssetRunnable, automatedPagingIntervalMs);
    }

    public void setPagingInterval(int pagingIntervalSeconds) {
        automatedPagingIntervalMs = pagingIntervalSeconds * 1000L;
    }

    private void goToNextAsset() {
        if (pagerAdapter.getCount() == 0) return;
        if (mPager.getCurrentItem() < Objects.requireNonNull(mPager.getAdapter()).getCount() - 1) {
            mPager.setCurrentItem(mPager.getCurrentItem() + 1, true);
        } else {
            mPager.setCurrentItem(0, true);
        }
    }

    public void onDestroy() {
        clearKeepScreenOnFlags();
        if (mainHandler != null) {
            mainHandler.removeCallbacks(goToNextAssetRunnable);
        }
    }

    private void clearKeepScreenOnFlags() {
        if (getContext() instanceof Activity) {
            // view is being triggered from main app, remove the flags to keep screen on
            Window window = ((Activity) getContext()).getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    public void addItems(@NotNull List<ScreenSaverItem> items) {
        int oldItemCount = this.items.size();
        this.items.addAll(items);
        pagerAdapter.notifyDataSetChanged();

        // ensure timer is started if we have more than 1 item for the first time
        if (oldItemCount <= 1 && this.items.size() > 1) {
            startTimerNextAsset();
        }
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {
        ScreenSaverItem screenSaverItem = items.get(i);
        StringBuilder sb = new StringBuilder();
        boolean hasLeftCountry = screenSaverItem.getLeftCountry() != null;
        if (screenSaverItem.getRightUrl() == null) {
            if (hasLeftCountry) {
                sb.append(screenSaverItem.getLeftCity())
                        .append(", ").append(screenSaverItem.getLeftCountry());
            }
        } else {
            if (hasLeftCountry) {
                sb.append(screenSaverItem.getLeftCountry());
            }
            if (screenSaverItem.getRightCountry() != null) {
                if (hasLeftCountry) {
                    if (!Objects.equals(screenSaverItem.getLeftCountry(), screenSaverItem.getRightCountry())) {
                        sb.append(" • ").append(screenSaverItem.getRightCountry());
                    }
                } else {
                    sb.append(screenSaverItem.getRightCountry());
                }
            }
        }
        subtitleTextView.setText(sb.toString());
    }

    @Override
    public void onPageSelected(int i) {
        startTimerNextAsset();
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    private static class ScreenSaverPagerAdapter extends PagerAdapter {
        private final Context context;
        private List<ScreenSaverItem> items;

        private ScreenSaverPagerAdapter(Context context,
                                        List<ScreenSaverItem> items) {
            this.context = context;
            this.items = items;
        }

        public void setItems(List<ScreenSaverItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        private void loadImage(AppCompatImageView imageView, String url) {
            RequestBuilder<Drawable> glideLoader = Glide.with(context)
                    .load(url)
                    .centerInside()
                    // .placeholder(context.getResources().getDrawable(com.zeuskartik.mediaslider.R.drawable.images))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Timber.e(e, "Could not fetch image: %s", model);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    });
            glideLoader.into(imageView);
        }

        @NonNull
        @Override
        @SuppressLint("UnsafeOptInUsageError")
        public Object instantiateItem(@NonNull ViewGroup container, final int position) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(context.LAYOUT_INFLATER_SERVICE);
            View view;
            ScreenSaverItem model = items.get(position);
            if (model.getRightUrl() == null) {
                view = inflater.inflate(R.layout.screensaver_image_single, container, false);
                AppCompatImageView imageView = view.findViewById(R.id.image);
                loadImage(imageView, model.getLeftUrl());
            } else {
                view = inflater.inflate(R.layout.screensaver_image_double, container, false);
                AppCompatImageView leftImage = view.findViewById(R.id.left_image);
                loadImage(leftImage, model.getLeftUrl());
                AppCompatImageView rightImage = view.findViewById(R.id.right_image);
                loadImage(rightImage, model.getRightUrl());
            }
            container.addView(view);
            return view;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return (view == o);
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            View view = (View) object;
            container.removeView(view);
        }
    }
}

