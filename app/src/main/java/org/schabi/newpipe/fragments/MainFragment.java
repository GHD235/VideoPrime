package org.schabi.newpipe.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.twitch.extractors.TwitchLiveKioskExtractor;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.videoprime.BottomBar;
import org.schabi.newpipe.videoprime.HomeFragment;
import org.schabi.newpipe.videoprime.LiveFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * VideoPrime: the main screen. Its four sections (home feed, live channels, subscriptions and
 * library) are fixed and switched with a bottom navigation bar.
 */
public class MainFragment extends BaseFragment {
    private FragmentMainBinding binding;
    /** The activity's bottom navigation bar, shown while this fragment's view exists. */
    @Nullable
    private BottomNavigationView bottomBar;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);

        bottomBar = BottomBar.view(requireActivity());
        if (bottomBar == null) {
            return;
        }
        BottomBar.setShown(requireActivity(), true);
        // leave room for the bar, which is drawn over the bottom of the screen
        final View.OnLayoutChangeListener padForBar = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (binding != null && rootView.getPaddingBottom() != v.getHeight()) {
                rootView.setPadding(0, 0, 0, v.getHeight());
                BottomBar.setShown(requireActivity(), true);
            }
        };
        bottomBar.addOnLayoutChangeListener(padForBar);
        rootView.setTag(R.id.main_bottom_navigation, padForBar);
        rootView.setPadding(0, 0, 0, BottomBar.shownHeight(requireActivity()));

        bottomBar.setOnItemSelectedListener(item -> {
            binding.pager.setCurrentItem(item.getItemId(), false);
            updateTitleForTab(item.getItemId());
            return true;
        });
        binding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                if (bottomBar != null) {
                    bottomBar.getMenu().getItem(position).setChecked(true);
                }
                updateTitleForTab(position);
            }
        });

        setupTabs();
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }
        if (binding != null) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        if (bottomBar != null) {
            final Object padForBar = getView() == null
                    ? null : getView().getTag(R.id.main_bottom_navigation);
            if (padForBar instanceof View.OnLayoutChangeListener) {
                bottomBar.removeOnLayoutChangeListener((View.OnLayoutChangeListener) padForBar);
            }
            bottomBar.setOnItemSelectedListener(null);
            BottomBar.setShown(requireActivity(), false);
            bottomBar = null;
        }
        super.onDestroyView();
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Sections
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * @return the fixed sections of the bottom navigation, in order
     */
    private static List<Tab> getSections() {
        return List.of(
                new HomeTab(),
                Tab.Type.SUBSCRIPTIONS.getTab(),
                new LiveTab(),
                new Tab.LibraryTab());
    }

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(getSections());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        final int currentItem = binding.pager.getCurrentItem();
        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);
        binding.pager.setOffscreenPageLimit(tabsList.size());

        if (bottomBar == null) {
            return;
        }
        final Menu menu = bottomBar.getMenu();
        menu.clear();
        for (int i = 0; i < tabsList.size(); i++) {
            final Tab tab = tabsList.get(i);
            menu.add(Menu.NONE, i, i, tab.getTabName(requireContext()))
                    .setIcon(tab.getTabIconRes(requireContext()));
        }
        binding.pager.setCurrentItem(currentItem, false);
        menu.getItem(currentItem).setChecked(true);
        updateTitleForTab(currentItem);
    }

    private void updateTitleForTab(final int tabPosition) {
        if (tabsList.isEmpty() || tabPosition >= tabsList.size()) {
            return;
        }
        // the home section shows the app name, like the mockups
        setTitle(tabsList.get(tabPosition) instanceof HomeTab
                ? getString(R.string.videoprime_title)
                : tabsList.get(tabPosition).getTabName(requireContext()));
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    /** "Home": the new videos and broadcasts of the subscribed channels. */
    private static final class HomeTab extends Tab.FeedTab {
        @Override
        public HomeFragment getFragment(final Context context) {
            return new HomeFragment();
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.nav_home);
        }

        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_home;
        }
    }

    /** "Live": the most watched live streams of Twitch or YouTube. */
    private static final class LiveTab extends Tab.KioskTab {
        LiveTab() {
            super(ServiceList.Twitch.getServiceId(), TwitchLiveKioskExtractor.KIOSK_ID);
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.nav_live);
        }

        @Override
        public LiveFragment getFragment(final Context context) {
            return new LiveFragment();
        }
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
