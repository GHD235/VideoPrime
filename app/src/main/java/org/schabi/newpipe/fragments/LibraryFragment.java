package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentLibraryBinding;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.videoprime.DownloadsFragment;
import org.schabi.newpipe.videoprime.QueuesFragment;

/**
 * VideoPrime: the "Library" section of the bottom navigation. Shortcuts to history,
 * downloads, settings and about, followed by the saved playlists.
 */
public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.libraryHistory.setOnClickListener(v ->
                NavigationHelper.openStatisticFragment(
                        requireActivity().getSupportFragmentManager()));
        binding.libraryDownloads.setOnClickListener(v ->
                DownloadsFragment.open(requireActivity().getSupportFragmentManager()));
        binding.libraryQueues.setOnClickListener(v ->
                QueuesFragment.open(requireActivity().getSupportFragmentManager()));
        binding.librarySettings.setOnClickListener(v ->
                NavigationHelper.openSettings(requireContext()));
        binding.libraryAbout.setOnClickListener(v ->
                NavigationHelper.openAbout(requireContext()));

        if (savedInstanceState == null) {
            final BookmarkFragment bookmarks = new BookmarkFragment();
            bookmarks.useAsFrontPage(true);
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.library_bookmarks, bookmarks)
                    .commit();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
