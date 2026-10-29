package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.videoprime.ChannelVisits;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class StreamMiniInfoItemHolder extends InfoItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;

    StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                             final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        // VideoPrime: rounded thumbnails, like the rest of the app
        ChannelVisits.roundCorners(itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            // PulsePlay: the watched part is read from the database in the background; reading it
            // here waited on the main thread for the database, busy with the feed in the
            // background, and the app stopped responding while a list was laid out
            itemProgressView.setVisibility(View.GONE);
            loadProgress(item, historyRecordManager, state -> {
                if (state != null) {
                    itemProgressView.setVisibility(View.VISIBLE);
                    itemProgressView.setMax((int) item.getDuration());
                    itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                            .toSeconds(state.getProgressMillis()));
                }
            });
        } else if (StreamTypeUtil.isLiveStream(item.getStreamType())) {
            cancelProgress();
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            cancelProgress();
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView, item.getThumbnails());
        // VideoPrime: published since the last visit to its channel
        itemThumbnailView.setForeground(ChannelVisits.isNew(item)
                ? ChannelVisits.badge(itemThumbnailView.getContext()) : null);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().selected(item);
            }
        });

        switch (item.getStreamType()) {
            case AUDIO_STREAM:
            case VIDEO_STREAM:
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
            case POST_LIVE_STREAM:
            case POST_LIVE_AUDIO_STREAM:
                enableLongClick(item);
                break;
            case NONE:
            default:
                disableLongClick();
                break;
        }
    }

    @Override
    public void updateState(final InfoItem infoItem,
                            final HistoryRecordManager historyRecordManager) {
        final StreamInfoItem item = (StreamInfoItem) infoItem;
        loadProgress(item, historyRecordManager, state -> showUpdatedProgress(item, state));
    }

    private void showUpdatedProgress(final StreamInfoItem item,
                                     @Nullable final StreamStateEntity state) {
        if (state != null && item.getDuration() > 0
                && !StreamTypeUtil.isLiveStream(item.getStreamType())) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
    }

    /** The item whose progress is being read: a row reused for another item ignores the old one. */
    @Nullable
    private String progressUrl;
    @Nullable
    private Disposable progressRead;

    private void cancelProgress() {
        progressUrl = null;
        if (progressRead != null) {
            progressRead.dispose();
            progressRead = null;
        }
    }

    private void loadProgress(final StreamInfoItem item,
                              final HistoryRecordManager historyRecordManager,
                              final Consumer<StreamStateEntity> onState) {
        cancelProgress();
        if (!DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())) {
            onState.accept(null);
            return;
        }
        final String url = item.getUrl();
        progressUrl = url;
        progressRead = historyRecordManager.loadStreamState(item)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (url.equals(progressUrl)) {
                        onState.accept(states[0]);
                    }
                }, error -> { });
    }

    private void enableLongClick(final StreamInfoItem item) {
        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnStreamSelectedListener() != null) {
                itemBuilder.getOnStreamSelectedListener().held(item);
            }
            return true;
        });
    }

    private void disableLongClick() {
        itemView.setLongClickable(false);
        itemView.setOnLongClickListener(null);
    }
}
