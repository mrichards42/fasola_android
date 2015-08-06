package org.fasola.fasolaminutes;

import android.content.Intent;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends SimpleTabActivity {
    public final static String ACTIVITY_POSITION = "org.fasola.fasolaminutes.POSITION";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Save all the pages since the queries may take some time to run
        mViewPager.setOffscreenPageLimit(mSectionsPagerAdapter.getCount());
        // Change title, FaSoLa tabs, and search when the page changes
        final FasolaTabView tabs = (FasolaTabView) findViewById(R.id.fasola_tabs);
        setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                setTitle(mSectionsPagerAdapter.getPageTitle(position));
                tabs.setSelection(position);
            }
        });
        // Initial settings
        setTitle(mSectionsPagerAdapter.getPageTitle(0));
        tabs.setSelection(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Kill the service
        stopService(new Intent(this, PlaybackService.class));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Change to the requested fragment (by position)
        int position = intent.getIntExtra(ACTIVITY_POSITION, -1);
        if (position != -1)
            mViewPager.setCurrentItem(position, true);
    }


    @Override
    protected void onCreateTabs() {
        addTab(getString(R.string.tab_leaders), LeaderListFragment.class);
        addTab(getString(R.string.tab_songs), SongListFragment.class);
        addTab(getString(R.string.tab_singings), SingingListFragment.class);
        addTab(getString(R.string.tab_playlist), PlaylistFragment.class);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public static class LeaderListFragment extends CursorStickyListFragment {
        protected int mSortId = R.id.menu_leader_sort_name;
        protected final static String BUNDLE_SORT = "SORT_ID";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setIntentActivity(LeaderActivity.class);
            setItemLayout(R.layout.leader_list_item);
            if (savedInstanceState != null)
                mSortId = savedInstanceState.getInt(BUNDLE_SORT, mSortId);
            setHasOptionsMenu(true);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            updateQuery();
        }

        @Override
        public void onSaveInstanceState(final Bundle saveInstanceState) {
            super.onSaveInstanceState(saveInstanceState);
            saveInstanceState.putSerializable(BUNDLE_SORT, mSortId);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.menu_leader_fragment, menu);
            // Check the initial sort
            MenuItem item = menu.findItem(mSortId);
            if (item != null)
                item.setChecked(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            // Sort
            if (item.getGroupId() == R.id.menu_group_leader_sort) {
                item.setChecked(true);
                mSortId = item.getItemId();
                updateQuery();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        // Change query/index based on the selected sort column

        @Override
        public void updateQuery() {
            switch(mSortId) {
                case R.id.menu_leader_sort_count:
                    setBins(0, 10, 50, 100, 500, 1000);
                    showHeaders(false);
                    setQuery(C.Leader.selectList(C.Leader.fullName, C.Leader.leadCount.format("'(' || {column} || ')'"))
                                        .sectionIndex(C.Leader.leadCount, "DESC"));
                    break;
                case R.id.menu_leader_sort_entropy:
                    setBins(0, 10, 20, 30, 40, 50, 60, 70, 80, 90);
                    showHeaders(false);
                    setQuery(C.Leader.selectList(C.Leader.fullName, C.Leader.entropyDisplay.format("'(' || {column} || ')'"))
                                     .sectionIndex(C.Leader.entropy.format("CAST({column} * 100 AS INT)"), "DESC"));
                    break;
                case R.id.menu_leader_sort_first_name:
                    setAlphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                    showHeaders(true);
                    setQuery(C.Leader.selectList(C.Leader.fullName, C.Leader.leadCount.format("'(' || {column} || ')'"))
                                  .sectionIndex(C.Leader.fullName, "ASC"));
                    break;
                case R.id.menu_leader_sort_name:
                default:
                    setAlphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                    showHeaders(true);
                    setQuery(C.Leader.selectList(C.Leader.fullName, C.Leader.leadCount.format("'(' || {column} || ')'"))
                                  .sectionIndex(C.Leader.lastName, "ASC"));
                    break;
            }
        }

        @Override
        public void onSearch(SQL.Query query, String searchTerm) {
            setQuery(query.where(C.Leader.fullName, "LIKE", "%" + searchTerm + "%")
                    .or(C.LeaderAlias.alias, "LIKE", "%" + searchTerm + "%"));
        }
    }

    public static class SongListFragment extends CursorStickyListFragment {
        protected int mSortId = R.id.menu_song_sort_page;
        protected final static String BUNDLE_SORT = "SORT_ID";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setIntentActivity(SongActivity.class);
            setItemLayout(R.layout.song_list_item);
            if (savedInstanceState != null) {
                mSortId = savedInstanceState.getInt(BUNDLE_SORT, mSortId);
            }
            setHasOptionsMenu(true);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            updateQuery();
        }

        @Override
        public void onSaveInstanceState(final Bundle saveInstanceState) {
            super.onSaveInstanceState(saveInstanceState);
            saveInstanceState.putSerializable(BUNDLE_SORT, mSortId);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.menu_song_fragment, menu);
            // Check the initial sort
            MenuItem item = menu.findItem(mSortId);
            if (item != null)
                item.setChecked(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            // Sort
            if (item.getGroupId() == R.id.menu_group_song_sort) {
                item.setChecked(true);
                mSortId = item.getItemId();
                updateQuery();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        /**
         * Get the default query for filtering or sorting
         * @return {SQL.Query} Default song query
         */
        private SQL.Query songQuery() {
            return C.Song.selectList(C.Song.number, C.Song.title,
                                     C.SongStats.leadCount.sum().format("'(' || {column} || ')'"));
        }

        // Change query/index based on the selected sort column
        public void updateQuery() {
            switch(mSortId) {
                case R.id.menu_song_sort_title:
                    setAlphabet("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
                    showHeaders(true);
                    setQuery(songQuery().sectionIndex(C.Song.title, "ASC"));
                    break;
                case R.id.menu_song_sort_leads:
                    setBins(100, 500, 1000, 1500, 2000, 2500, 3000);
                    showHeaders(false);
                    setQuery(songQuery().sectionIndex(C.SongStats.leadCount.sum(), "DESC"));
                    break;
                case R.id.menu_song_sort_page:
                default:
                    setBins(0, 100, 200, 300, 400, 500);
                    showHeaders(false);
                    setQuery(songQuery().sectionIndex(C.Song.pageSort, "ASC"));
                    break;
            }
        }

        @Override
        public void onSearch(SQL.Query query, String searchTerm) {
            searchTerm = DatabaseUtils.sqlEscapeString("%" + searchTerm + "%");
            showHeaders(true);
            setBins("[1]Title", "[2]Composer", "[3]Poet", "[4]Words");
            setQuery(songQuery().sectionIndex(
                    new SQL.QueryColumn(
                            "CASE ",
                            C.Song.fullName.format("WHEN {column} LIKE %s THEN '[1]Title' ", searchTerm),
                            C.Song.composer.format("WHEN {column} LIKE %s THEN '[2]Composer' ", searchTerm),
                            C.Song.poet.format("WHEN {column} LIKE %s THEN '[3]Poet' ", searchTerm),
                            C.Song.lyrics.format("WHEN {column} LIKE %s THEN '[4]Words' ", searchTerm),
                            "END"
                    ), "ASC")
                .where(SQL.INDEX_COLUMN, "IS NOT", "NULL"));
        }
    }

    public static class SingingListFragment extends CursorStickyListFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setIntentActivity(SingingActivity.class);
            setItemLayout(R.layout.singing_list_item);
            setQuery(C.Singing.selectList(C.Singing.name, C.Singing.startDate, C.Singing.location)
                                .sectionIndex(C.Singing.year));
            setRangeIndexer();
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.menu_singing_fragment, menu);
        }


        @Override
        public void onSearch(SQL.Query query, String searchTerm) {
            setQuery(query.where(C.Singing.name, "LIKE", "%" + searchTerm + "%")
                    .or(C.Singing.location, "LIKE", "%" + searchTerm + "%"));
        }
    }
}