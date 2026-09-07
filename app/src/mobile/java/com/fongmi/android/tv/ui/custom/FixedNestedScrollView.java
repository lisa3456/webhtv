package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

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
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return super.onTouchEvent(ev);
    }

    @Override
    public void scrollTo(int x, int y) {
        // 不限制滚动距离
        super.scrollTo(x, y);
    }

    @Override
    public void fling(int velocityY) {
        super.fling(velocityY);
    }

    // 新增：确保可以滚动到任意位置
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    // 新增：计算可滚动高度
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return super.computeScrollDeltaToGetChildRectOnScreen(rect);
    }
}