package org.schabi.newpipe.local.dialog;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.DEFAULT_THUMBNAIL_ID;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.videoprime.PlaylistPicker;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private PlaylistPicker picker;

    private final CompositeDisposable playlistDisposables = new CompositeDisposable();

    /**
     * Create a new instance of {@link PlaylistAppendDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistAppendDialog}
     */
    public static PlaylistAppendDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

    // VideoPrime: a sheet from the bottom drawn in Compose, like the other menus
    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), getTheme());
        dialog.setOnShowListener(ignored -> {
            // the sheet draws its own rounded background
            final View sheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
        picker = new PlaylistPicker(requireContext(), this::openCreatePlaylistDialog,
                selected -> {
                    final List<StreamEntity> entities = getStreamEntities();
                    if (entities != null) {
                        onPlaylistSelected(playlistManager, selected, entities);
                    }
                });
        playlistDisposables.add(playlistManager
                .getPlaylistDuplicates(getStreamEntities().get(0).getUrl())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistsReceived));
        return picker.getView();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistDisposables.dispose();
        picker = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    /** Display create playlist dialog. */
    public void openCreatePlaylistDialog() {
        if (getStreamEntities() == null || !isAdded()) {
            return;
        }

        final PlaylistCreationDialog playlistCreationDialog =
                PlaylistCreationDialog.newInstance(getStreamEntities());
        // Move the dismissListener to the new dialog.
        playlistCreationDialog.setOnDismissListener(this.getOnDismissListener());
        this.setOnDismissListener(null);

        playlistCreationDialog.show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }

    private void onPlaylistsReceived(@NonNull final List<PlaylistDuplicatesEntry> playlists) {
        if (picker != null) {
            picker.setPlaylists(playlists);
        }
    }

    private void onPlaylistSelected(@NonNull final LocalPlaylistManager manager,
                                    @NonNull final PlaylistDuplicatesEntry playlist,
                                    @NonNull final List<StreamEntity> streams) {

        final String toastText;
        if (playlist.getTimesStreamIsContained() > 0) {
            toastText = getString(R.string.playlist_add_stream_success_duplicate,
                    playlist.getTimesStreamIsContained());
        } else {
            toastText = getString(R.string.playlist_add_stream_success);
        }

        final Toast successToast = Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT);

        playlistDisposables.add(manager.appendToPlaylist(playlist.getUid(), streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                    successToast.show();

                    if (playlist.getThumbnailStreamId() != null
                            && playlist.getThumbnailStreamId() == DEFAULT_THUMBNAIL_ID
                    ) {
                        playlistDisposables.add(manager
                                .changePlaylistThumbnail(playlist.getUid(), streams.get(0).getUid(),
                                        false)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(ignore -> successToast.show()));
                    }
                }));

        requireDialog().dismiss();
    }
}
