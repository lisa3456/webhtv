package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.BaseGridView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.DialogEpisodeListBinding;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class EpisodeListDialog extends BaseBottomSheetDialog implements FlagAdapter.OnClickListener, ArrayAdapter.OnClickListener, EpisodeAdapter.OnClickListener {

    private final List<Integer> segmentStarts;

    private DialogEpisodeListBinding binding;
    private EpisodeAdapter episodeAdapter;
    private ArrayAdapter arrayAdapter;
    private FlagAdapter flagAdapter;
    private DialogInterface.OnDismissListener dismissListener;
    private List<Flag> flags;
    private int panelWidth;

    public EpisodeListDialog() {
        segmentStarts = new ArrayList<>();
    }

    public static EpisodeListDialog create() {
        return new EpisodeListDialog();
    }

    public EpisodeListDialog flags(List<Flag> flags) {
        this.flags = flags;
        return this;
    }

    public EpisodeListDialog dismissListener(DialogInterface.OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof EpisodeListDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        binding = DialogEpisodeListBinding.inflate(inflater, container, false);
        return binding;
    }

    @Override
    protected void initView() {
        panelWidth = getPanelWidth();
        setRecyclerView();
        flagAdapter.addAll(flags == null ? new ArrayList<>() : flags);
        binding.flag.setSelectedPosition(flagAdapter.getPosition());
        setEpisodes(getSelectedFlag());
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        super.setBehavior(dialog);
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;

        int halfScreen = ResUtil.getScreenHeight(requireContext()) / 2;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = halfScreen;
        sheet.setLayoutParams(params);

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(halfScreen);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

    private void setRecyclerView() {
        int spacing = ResUtil.dp2px(8);
        binding.flag.setHorizontalSpacing(spacing);
        binding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.flag.setAdapter(flagAdapter = new FlagAdapter(this));
        flagAdapter.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.setHorizontalSpacing(spacing);
        binding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        binding.array.setAdapter(arrayAdapter = new ArrayAdapter(this));
        arrayAdapter.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.array.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        binding.episode.setHorizontalSpacing(spacing);
        binding.episode.setVerticalSpacing(spacing);
        binding.episode.setAdapter(episodeAdapter = new EpisodeAdapter(this, getEpisodeContentWidth()));
        episodeAdapter.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        binding.flag.setOnKeyListener((view, keyCode, event) -> onFlagKey(event));
        binding.array.addOnChildViewHolderSelectedListener(new androidx.leanback.widget.OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && position >= 0 && position < segmentStarts.size()) scrollToEpisode(segmentStarts.get(position), false);
            }
        });
    }

    private int getPanelWidth() {
        int screen = ResUtil.getScreenWidth(requireContext());
        return Math.max(ResUtil.dp2px(420), Math.min(ResUtil.dp2px(680), Math.round(screen * 0.42f)));
    }

    private int getEpisodeContentWidth() {
        return panelWidth - ResUtil.dp2px(40);
    }

    private Flag getSelectedFlag() {
        if (flagAdapter == null || flagAdapter.getItemCount() == 0) return null;
        return flagAdapter.get(flagAdapter.getPosition());
    }

    private void setEpisodes(Flag flag) {
        if (flag == null) return;
        List<Episode> episodes = flag.getEpisodes();
        int column = EpisodeAdapter.getColumn(episodes, getEpisodeContentWidth());
        binding.episode.setNumColumns(column);
        episodeAdapter.setColumn(column);
        episodeAdapter.addAll(episodes);
        setSegments(episodes.size());
        scrollToSelectedEpisode();
    }

    private void setSegments(int size) {
        int segment = getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        segmentStarts.clear();
        if (size > segment) for (int i = 0; i < size; i += segment) {
            segmentStarts.add(i);
            items.add((i + 1) + "-" + Math.min(i + segment, size));
        }
        arrayAdapter.setSegmentSize(segment);
        arrayAdapter.addAll(items);
        binding.array.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        flagAdapter.setNextFocusDown(items.isEmpty() ? R.id.episode : R.id.array);
        arrayAdapter.setNextFocus(R.id.flag, R.id.episode);
        episodeAdapter.setNextFocusUp(items.isEmpty() ? R.id.flag : R.id.array);
        episodeAdapter.setNextFocusDown(0);
    }

    private boolean onFlagKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        focusLowerFromFlag();
        return true;
    }

    private boolean onArrayKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            focusFlag();
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            focusEpisodeFromArray();
            return true;
        }
        return false;
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        int position = getFocusedPosition(binding.episode);
        int column = Math.max(1, episodeAdapter.getColumn());
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        focusUpperFromEpisode();
        return true;
    }

    private void focusUpperFromEpisode() {
        if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusFlag();
    }

    private void focusLowerFromFlag() {
        if (isVisible(binding.array) && arrayAdapter.getItemCount() > 0) focusArray();
        else focusPosition(binding.episode, episodeAdapter.getPosition());
    }

    private void focusEpisodeFromArray() {
        int position = binding.array.getSelectedPosition();
        int start = position >= 0 && position < segmentStarts.size() ? segmentStarts.get(position) : episodeAdapter.getPosition();
        focusPosition(binding.episode, start);
    }

    private void focusFlag() {
        focusPosition(binding.flag, flagAdapter.getPosition());
    }

    private void focusArray() {
        int position = Math.max(0, binding.array.getSelectedPosition());
        focusPosition(binding.array, position);
    }

    private int getFocusedPosition(RecyclerView recycler) {
        View child = recycler.getFocusedChild();
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private void focusPosition(BaseGridView grid, int position) {
        if (grid.getAdapter() == null || grid.getAdapter().getItemCount() == 0) return;
        position = Math.max(0, Math.min(position, grid.getAdapter().getItemCount() - 1));
        int target = position;
        grid.setSelectedPosition(target);
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
            else grid.requestFocus();
        });
    }

    private boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    private int getEpisodeSegmentSize(int size) {
        return size <= 60 ? 20 : 40;
    }

    private void scrollToSelectedEpisode() {
        int position = episodeAdapter.getPosition();
        scrollToSegment(position);
        scrollToEpisode(position, true);
    }

    private void scrollToSegment(int episodePosition) {
        if (segmentStarts.isEmpty()) return;
        int target = 0;
        for (int i = 0; i < segmentStarts.size(); i++) if (episodePosition >= segmentStarts.get(i)) target = i;
        binding.array.setSelectedPosition(target);
        binding.array.scrollToPosition(target);
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= episodeAdapter.getItemCount()) return;
        binding.episode.post(() -> {
            if (binding == null) return;
            binding.episode.setSelectedPositionSmooth(position);
            if (!requestFocus) return;
            binding.episode.postDelayed(() -> {
                if (binding == null) return;
                binding.episode.setSelectedPositionSmooth(position);
                binding.episode.requestFocus();
            }, 150);
        });
    }

    @Override
    public void onItemClick(Flag item) {
        ((FlagAdapter.OnClickListener) requireActivity()).onItemClick(item);
        flagAdapter.notifyItemRangeChanged(0, flagAdapter.getItemCount());
        setEpisodes(item);
    }

    @Override
    public void onItemClick(Episode item) {
        ((EpisodeAdapter.OnClickListener) requireActivity()).onItemClick(item);
        dismiss();
    }

    @Override
    public void onRevSort() {
    }

    @Override
    public void onRevPlay(TextView view) {
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) dismissListener.onDismiss(dialog);
    }
}