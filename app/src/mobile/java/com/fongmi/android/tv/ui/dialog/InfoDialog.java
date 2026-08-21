package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogInfoBinding;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class InfoDialog extends BaseAlertDialog {

    private DialogInfoBinding binding;
    private String header;
    private String title;
    private String url;

    public static InfoDialog create() {
        return new InfoDialog();
    }

    public InfoDialog title(CharSequence title) {
        this.title = TextUtils.isEmpty(title) ? "" : title.toString();
        return this;
    }

    public InfoDialog headers(Map<String, String> header) {
        this.header = buildHeader(header);
        return this;
    }

    public InfoDialog url(String url) {
        this.url = TextUtils.isEmpty(url) ? "" : url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogInfoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(ResUtil.isLand(requireContext()) ? 0.62f : 0.92f);
        configureWindow();  // ← 添加这行
    }

    // ========== 新增：配置窗口 ==========
    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        // 关键：设置透明背景，让布局背景显示出来
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
    }

    @Override
    protected void initView() {
        if (header == null) header = "";
        if (title == null) title = "";
        if (url == null) url = "";
        binding.url.setText(url);
        binding.title.setText(title);
        binding.header.setText(header);
        binding.title.setSingleLine(title.contains(url));
        binding.url.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        binding.header.setVisibility(TextUtils.isEmpty(header) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void initEvent() {
        binding.url.setOnClickListener(this::onShare);
        binding.url.setOnLongClickListener(v -> onCopy(url));
        binding.header.setOnLongClickListener(v -> onCopy(header));
    }

    private void onShare(View view) {
        ((Listener) requireActivity()).onShare(title);
        dismiss();
    }

    private boolean onCopy(String text) {
        Util.copy(text);
        return true;
    }

    private String buildHeader(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String key : headers.keySet()) sb.append(key).append(" : ").append(headers.get(key)).append("\n");
        return Util.substring(sb.toString());
    }

    public interface Listener {

        void onShare(CharSequence title);
    }
}