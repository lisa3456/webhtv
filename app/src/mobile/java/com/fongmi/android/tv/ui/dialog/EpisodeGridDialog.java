package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.DialogEpisodeGridBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.fragment.EpisodeFragment;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends BaseBottomSheetDialog {

    private final List<String> titles;
    private DialogEpisodeGridBinding binding;
    private List<Episode> episodes;
    private boolean reverse;
    private int spanCount;
    private int itemCount;

    public EpisodeGridDialog() {
        this.titles = new ArrayList<>();
        this.spanCount = 5;
    }

    public static EpisodeGridDialog create() {
        return new EpisodeGridDialog();
    }

    public EpisodeGridDialog reverse(boolean reverse) {
        this.reverse = reverse;
        return this;
    }

    public EpisodeGridDialog episodes(List<Episode> episodes) {
        this.episodes = episodes;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof EpisodeGridDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 创建 BottomSheetDialog
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme()) {
            @Override
            public void onStart() {
                super.onStart();
                // 获取 BottomSheet 并配置
                configureBottomSheet(this);
            }
        };
        
        // 配置窗口属性
        configureWindow(dialog);
        
        // 手动 inflate 视图
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(R.layout.dialog_episode_grid, null);
        dialog.setContentView(view);
        
        // 绑定视图
        binding = DialogEpisodeGridBinding.bind(view);
        
        // 初始化
        initView();
        initEvent();
        
        return dialog;
    }

    private void configureWindow(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowCompat.setDecorFitsSystemWindows(window, true);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        
        // 关键修改：设置窗口在底部
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void configureBottomSheet(BottomSheetDialog dialog) {
        if (dialog == null) return;
        
        // 获取 BottomSheet 的根视图
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        
        // 获取 BottomSheetBehavior
        BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(sheet);
        if (behavior == null) return;
        
        // 设置为折叠状态（底部显示）
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        
        // 禁用拖动
        behavior.setDraggable(false);
        
        // 允许内容自适应高度
        behavior.setFitToContents(true);
        
        // 设置 peek 高度为 0，让 BottomSheet 完全展开但只占内容高度
        behavior.setPeekHeight(0);
        
        // 设置为不可隐藏（不能滑动关闭）
        behavior.setHideable(false);
        
        // 计算最大高度 - 不超过屏幕的 70%
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        int statusBarHeight = getStatusBarHeight();
        int maxHeight = Math.min(
            (int) (screenHeight * 0.7f), 
            screenHeight - statusBarHeight - ResUtil.dp2px(20)
        );
        
        // 设置 BottomSheet 的布局参数
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        if (params != null) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            sheet.setLayoutParams(params);
        }
        
        // 设置最大高度
        if (sheet instanceof ViewGroup) {
            sheet.setMaxHeight(maxHeight);
        }
        
        sheet.requestLayout();
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return null;
    }

    @Override
    protected void initView() {
        if (binding == null) return;
        setSpanCount();
        setTitles();
        setPager();
    }

    @Override
    protected void initEvent() {
        if (binding == null) return;
        binding.column.setOnClickListener(this::onColumnToggle);
        getChildFragmentManager().setFragmentResultListener("result", this, (requestKey, bundle) -> {
            Episode episode = bundle.getParcelable("episode");
            if (episode != null) {
                ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(episode);
            }
            dismiss();
        });
    }

    private void onColumnToggle(View view) {
        PlayerSetting.putEpisodeColumn(spanCount == 1 ? 2 : 1);
        setSpanCount();
        setTitles();
        setPager();
    }

    private void setSpanCount() {
        if (episodes == null || episodes.isEmpty()) return;
        int avg = (int) Math.ceil(episodes.stream().mapToInt(e -> e.getName().length()).average().orElse(0));
        int max = episodes.stream().mapToInt(e -> e.getDesc().concat(e.getName()).length()).max().orElse(0);
        boolean longTitle = avg >= 8 || max >= 12;
        if (longTitle) spanCount = PlayerSetting.getEpisodeColumn();
        else if (avg >= 4) spanCount = 3;
        else if (avg >= 2) spanCount = 4;
        else spanCount = 5;
        itemCount = episodes.size() <= 60 ? 20 : spanCount * (ResUtil.isLand(requireActivity()) ? 5 : 10);
        if (binding != null) {
            binding.column.setVisibility(longTitle ? View.VISIBLE : View.GONE);
            binding.column.setImageResource(spanCount == 1 ? R.drawable.ic_site_double_column : R.drawable.ic_site_single_column);
        }
    }

    private void setTitles() {
        if (titles == null || episodes == null) return;
        titles.clear();
        if (reverse) {
            for (int i = episodes.size(); i > 0; i -= itemCount) {
                titles.add(i + " - " + Math.max(i - itemCount + 1, 1));
            }
        } else {
            for (int i = 0; i < episodes.size(); i += itemCount) {
                titles.add((i + 1) + " - " + Math.min(i + itemCount, episodes.size()));
            }
        }
    }

    private void setPager() {
        if (binding == null || titles == null || titles.isEmpty()) return;
        binding.tabs.removeAllTabs();
        binding.pager.setAdapter(new PageAdapter(this));
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> tab.setText(titles.get(position))).attach();
        setCurrentPage();
    }

    private void setCurrentPage() {
        if (episodes == null || binding == null) return;
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).isSelected()) {
                int page = i / itemCount;
                if (page < titles.size()) {
                    binding.pager.setCurrentItem(page);
                }
                break;
            }
        }
    }

    class PageAdapter extends FragmentStateAdapter {

        public PageAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            int start = position * itemCount;
            int end = Math.min(start + itemCount, episodes.size());
            if (start >= episodes.size()) {
                return EpisodeFragment.newInstance(spanCount, new ArrayList<>());
            }
            return EpisodeFragment.newInstance(spanCount, episodes.subList(start, end));
        }

        @Override
        public int getItemCount() {
            return titles == null ? 0 : titles.size();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}