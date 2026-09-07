package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

public class FixedNestedScrollView extends NestedScrollView {

    public FixedNestedScrollView(@NonNull Context context) {
        super(context);
    }

    public FixedNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FixedNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 允许拦截触摸事件，支持滚动
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 处理触摸事件，支持滚动
        return super.onTouchEvent(ev);
    }

    @Override
    public void scrollTo(int x, int y) {
        // 允许垂直滚动
        super.scrollTo(x, y);
    }

    @Override
    public void fling(int velocityY) {
        // 恢复惯性滑动
        super.fling(velocityY);
    }
}