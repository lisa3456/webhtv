package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

public class CustomKeyDown extends GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

    private static final int DISTANCE = 100;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector detector;
    private final AudioManager manager;
    private final Listener listener;
    private final Activity activity;
    private final View videoView;
    private boolean changeBright;
    private boolean changeVolume;
    private boolean changeSpeed;
    private boolean changeScale;
    private boolean changeTime;
    private boolean multiTouch;
    private boolean animating;
    private boolean touch;
    private boolean lock;
    private float bright;
    private float currentBright;
    private float volume;
    private float scale;
    private long time;
    // 缩放平移相关
    private float translateX = 0f;
    private float translateY = 0f;
    private float lastPointerX = 0f;
    private float lastPointerY = 0f;

    public static CustomKeyDown create(Activity activity, View videoView) {
        return new CustomKeyDown(activity, videoView);
    }

    private CustomKeyDown(Activity activity, View videoView) {
        this.manager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.scaleDetector = new ScaleGestureDetector(activity, this);
        this.detector = new GestureDetector(activity, this);
        this.listener = (Listener) activity;
        this.videoView = videoView;
        this.activity = activity;
        this.scale = 1.0f;
        applyBrightness();
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int pointerCount = e.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN) {
            multiTouch = false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true;
            if (pointerCount == 2) {
                lastPointerX = (e.getX(0) + e.getX(1)) / 2;
                lastPointerY = (e.getY(0) + e.getY(1)) / 2;
            }
        }
        if (action == MotionEvent.ACTION_UP) {
            listener.onTouchEnd();
        }
        if (changeBright && action == MotionEvent.ACTION_UP) {
            PlayerSetting.putBrightness(currentBright);
        }
        if (changeSpeed && action == MotionEvent.ACTION_UP) {
            listener.onSpeedEnd();
        }
        if (changeTime && action == MotionEvent.ACTION_UP) {
            listener.onSeekEnd(time);
        }

        if (pointerCount == 2) {
            // 缩放
            scaleDetector.onTouchEvent(e);

            // 双指拖动（缩放后平移查看）
            if (action == MotionEvent.ACTION_MOVE && scale > 1.0f) {
                float currentX = (e.getX(0) + e.getX(1)) / 2;
                float currentY = (e.getY(0) + e.getY(1)) / 2;
                float deltaX = currentX - lastPointerX;
                float deltaY = currentY - lastPointerY;

                if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
                    translateX += deltaX;
                    translateY += deltaY;
                    clampTranslation();
                    applyTransform();
                }
                lastPointerX = currentX;
                lastPointerY = currentY;
            }
            // 双指抬起时重置参考点
            if (action == MotionEvent.ACTION_POINTER_UP) {
                if (e.getPointerCount() >= 2) {
                    lastPointerX = (e.getX(0) + e.getX(1)) / 2;
                    lastPointerY = (e.getY(0) + e.getY(1)) / 2;
                }
            }
            return true;
        }

        return detector.onTouchEvent(e);
    }

    private void applyBrightness() {
        float brightness = PlayerSetting.getBrightness();
        if (brightness < 0) return;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
    }

    public void resetScale() {
        if (scale == 1.0f && translateX == 0 && translateY == 0) return;
        videoView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    videoView.setPivotY(videoView.getHeight() / 2.0f);
                    videoView.setPivotX(videoView.getWidth() / 2.0f);
                    scale = 1.0f;
                    translateX = 0f;
                    translateY = 0f;
                }).start();
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    public float getScale() {
        return scale;
    }

    private boolean isMultiple(MotionEvent e) {
        return e.getPointerCount() > 1;
    }

    private boolean isEdge(MotionEvent e) {
        return ResUtil.isEdge(App.get(), e, ResUtil.dp2px(24));
    }

    private boolean isSide(MotionEvent e) {
        int four = ResUtil.getScreenWidth(App.get()) / 4;
        float x = e.getRawX();
        return x <= four || x >= four * 3;
    }

    private void reset() {
        time = 0;
        touch = true;
        changeTime = false;
        changeSpeed = false;
        changeBright = false;
        changeVolume = false;
        bright = Util.getBrightness(activity);
        currentBright = bright;
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale || lock) return true;
        reset();
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (multiTouch || isEdge(e) || changeScale || lock) return;
        listener.onSpeedUp();
        changeSpeed = true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        // 单指拖动：已缩放时平移画面
        if (!isMultiple(e1) && !isEdge(e1) && !changeScale && !lock && !changeSpeed) {
            if (scale > 1.0f) {
                if (Math.abs(distanceX) > 3 || Math.abs(distanceY) > 3) {
                    translateX -= distanceX;
                    translateY -= distanceY;
                    applyTransform();
                }
                return true;
            }
        }
        // 未缩放时走原来的亮度/音量/快进逻辑
        if (isMultiple(e1) || isEdge(e1) || changeScale || lock || changeSpeed) return true;
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e1.getY() - e2.getY();
        if (touch) checkFunc(Math.abs(deltaX), Math.abs(deltaY), e2);
        if (changeTime) listener.onSeeking(time = (long) (deltaX * 50));
        if (changeBright) setBright(deltaY);
        if (changeVolume) setVolume(deltaY);
        return true;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale) return true;
        listener.onDoubleTap(e.getRawX(), ResUtil.getScreenWidth(App.get()));
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (isMultiple(e) || changeScale) return true;
        listener.onSingleTap(e.getRawX(), ResUtil.getScreenWidth(App.get()));
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        if (isMultiple(e1) || isEdge(e1) || isSide(e1) || changeScale || lock || animating) return true;
        checkFunc(e1, e2);
        return true;
    }

    private void checkFunc(float distanceX, float distanceY, MotionEvent e2) {
        if ((float) Math.sqrt(distanceX * distanceX + distanceY * distanceY) < ResUtil.dp2px(20)) return;
        if (distanceX >= distanceY) changeTime = true;
        else if (isSide(e2)) checkSide(e2);
        touch = false;
    }

    private void checkFunc(MotionEvent e1, MotionEvent e2) {
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        double angle = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
        if (angle > 70 && e1.getY() - e2.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(LiveSetting.isInvert() ? 24 : -24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingUp).withEndAction(() -> animating = false).start()).start();
        } else if (angle > 70 && e2.getY() - e1.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(LiveSetting.isInvert() ? -24 : 24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingDown).withEndAction(() -> animating = false).start()).start();
        }
    }

    private void checkSide(MotionEvent e2) {
        int half = ResUtil.getScreenWidth(App.get()) / 2;
        if (e2.getRawX() > half) changeVolume = true;
        else changeBright = true;
    }

    private void setBright(float deltaY) {
        int height = videoView.getMeasuredHeight();
        float brightness = deltaY * 2.0f / height + bright;
        if (brightness < 0) brightness = 0f;
        if (brightness > 1.0f) brightness = 1.0f;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
        currentBright = brightness;
        listener.onBright((int) (brightness * 100));
    }

    private void setVolume(float deltaY) {
        int height = videoView.getMeasuredHeight();
        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float deltaV = deltaY * 2.0f / height * maxVolume;
        float index = volume + deltaV;
        if (index > maxVolume) index = maxVolume;
        if (index < 0) index = 0;
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        listener.onVolume((int) (index / maxVolume * 100.0f));
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        if (changeBright || changeVolume || changeSpeed || changeTime || lock) return changeScale = false;
        return changeScale = true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        clampTranslation();
        App.post(() -> changeScale = false, 500);
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        float focusX = detector.getFocusX();
        float focusY = detector.getFocusY();

        float oldScale = scale;
        scale *= detector.getScaleFactor();
        scale = Math.max(1.0f, Math.min(scale, 10.0f));

        // 缩放时调整平移，使缩放中心点保持不变
        if (oldScale != 1.0f || scale != 1.0f) {
            float scaleChange = scale / oldScale;
            translateX = (translateX - focusX) * scaleChange + focusX;
            translateY = (translateY - focusY) * scaleChange + focusY;
        }

        clampTranslation();
        applyTransform();
        return true;
    }

    private void applyTransform() {
        videoView.setPivotX(0);
        videoView.setPivotY(0);
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
        videoView.setTranslationX(translateX);
        videoView.setTranslationY(translateY);
    }

    private void clampTranslation() {
        if (scale <= 1.0f) {
            translateX = 0;
            translateY = 0;
        }
    }

    public interface Listener {

        void onSeeking(long time);

        void onSeekEnd(long time);

        void onSpeedUp();

        void onSpeedEnd();

        void onBright(int progress);

        void onVolume(int progress);

        void onFlingUp();

        void onFlingDown();

        void onSingleTap();

        default void onSingleTap(float x, float width) {
            onSingleTap();
        }

        void onDoubleTap();

        default void onDoubleTap(float x, float width) {
            onDoubleTap();
        }

        void onTouchEnd();
    }
}