package org.schabi.newpipe.local.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.videoprime.NameSheet;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {

    /**
     * Create a new instance of {@link PlaylistCreationDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistCreationDialog}
     */
    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    // VideoPrime: a sheet from the bottom with a rounded field, like "add to a playlist"
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreamEntities() == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        final BottomSheetDialog dialog = NameSheet.dialog(requireContext(), getTheme());
        NameSheet.adjustForKeyboard(dialog);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final List<StreamEntity> entities = getStreamEntities();
        if (entities == null) {
            return null;
        }
        final String subtitle = entities.size() == 1
                ? getString(R.string.videoprime_playlist_will_add, entities.get(0).getTitle())
                : null;
        return NameSheet.view(requireContext(), getString(R.string.create_playlist),
                getString(R.string.videoprime_playlist_name_hint), subtitle,
                getString(R.string.videoprime_playlist_create_add), this::dismiss, name -> {
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);
                    playlistManager.createPlaylist(name, entities)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> successToast.show());
                });
    }
}
