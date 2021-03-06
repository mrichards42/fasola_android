/*
 * This file is part of FaSoLa Minutes for Android.
 * Copyright (c) 2016 Mike Richards. All rights reserved.
 */

package org.fasola.fasolaminutes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.mobeta.android.dslv.DragSortListView;

/**
 * A Fragment with a DragSortListView that shows a playlist
 */
public class PlaylistFragment extends ListFragment
        implements DragSortListView.DropListener {

    DragSortListView mList;
    PlaybackService.Control mPlayer;
    Playlist mPlaylist;

    public PlaylistFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist, container, false);
        mList = (DragSortListView)view.findViewById(android.R.id.list);
        mPlaylist = Playlist.getInstance();
        mList.setEmptyView(view.findViewById(android.R.id.empty));
        setHasOptionsMenu(true);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getListAdapter() == null)
            setListAdapter(new PlaylistListAdapter(getActivity(), mPlaylist));
        // Setup list
        mList.setDropListener(this);
        mList.setFastScrollEnabled(true);
        // Setup MediaController
        mPlayer = new PlaybackService.Control(getActivity());
        // Setup recording help link
        view.findViewById(R.id.empty_playlist_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HelpActivity.start(getActivity(), R.string.help_recordings);
            }
        });
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        if (isVisibleToUser) {
            if (mList != null)
                mList.setSelection(mPlaylist.getPosition());
            Activity activity = getActivity();
            if (activity instanceof BaseActivity && ((BaseActivity)activity).isDrawerVisible(this)) {
                activity.setTitle(R.string.title_playlist);
                if (activity.getActionBar() != null)
                    activity.getActionBar().setSubtitle(String.format("%d items", mPlaylist.size()));
            }
        }
        super.setUserVisibleHint(isVisibleToUser);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BaseAdapter)getListAdapter()).notifyDataSetChanged();
        mObserver.registerPlaylistObserver();
    }

    @Override
    public void onPause() {
        mObserver.unregister();
        super.onPause();
    }

    PlaylistObserver mObserver = new PlaylistObserver() {
        @Override
        public void onPlaylistChanged() {
            Activity activity = getActivity();
            if (activity instanceof BaseActivity &&
                    ((BaseActivity)activity).isDrawerVisible(PlaylistFragment.this) &&
                    activity.getActionBar() != null) {
                activity.getActionBar().setSubtitle(String.format("%d items", mPlaylist.size()));
            }
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_playlist_fragment, menu);
        if (getActivity() instanceof NowPlayingActivity)
            menu.findItem(R.id.menu_now_playing).setVisible(false);
        if (menu.findItem(R.id.menu_help) == null)
            inflater.inflate(R.menu.menu_help, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_now_playing) {
            startActivity(new Intent(getActivity(), NowPlayingActivity.class));
            return true;
        }
        else if (item.getItemId() == R.id.menu_clear_playlist) {
            mPlaylist.clear();
            return true;
        } else if (item.getItemId() == R.id.menu_help) {
            return HelpActivity.start(getActivity(), R.string.help_playlist);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mPlayer.start(position);
    }

    View.OnClickListener mRemoveClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = getListView().getPositionForView(v);
            mPlaylist.remove(position);
        }
    };

    @Override
    public void drop(int from, int to) {
        mPlaylist.move(from, to);
        ((BaseAdapter)getListAdapter()).notifyDataSetChanged();
    }

    /**
     * Custom ListAdapter backed by the PlaybackService's playlist
     */
    class PlaylistListAdapter extends BaseAdapter {
        Context mContext;
        LayoutInflater mInflater;
        Playlist mPlaylist;

        public PlaylistListAdapter(Context context, Playlist playlist) {
            mContext = context;
            mPlaylist = playlist;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mPlaylist.size();
        }

        @Override
        public Object getItem(int i) {
            return mPlaylist.get(i);
        }

        public Playlist.Song getSong(int i) {
            return (Playlist.Song)getItem(i);
        }

        @Override
        public long getItemId(int i) {
            return getSong(i).leadId;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null)
                view = mInflater.inflate(R.layout.list_item_playlist, viewGroup, false);
            // Text
            Playlist.Song song = getSong(i);
            ((TextView) view.findViewById(android.R.id.text1)).setText(song.name);
            ((TextView) view.findViewById(android.R.id.text2)).setText(String.format("%s %s", song.year, song.singing));
            ((TextView) view.findViewById(R.id.text3)).setText(song.leaders);
            // Status indicator
            int iconResource = 0;
            if (mPlaylist.getPosition() == i)
                iconResource = R.drawable.ic_play_indicator;
            else if (mPlaylist.get(i) != null && mPlaylist.get(i).status == Playlist.Song.STATUS_ERROR)
                iconResource = R.drawable.ic_warning_amber_18dp;
            ((TextView) view.findViewById(android.R.id.text1))
                    .setCompoundDrawablesWithIntrinsicBounds(iconResource, 0, 0, 0);
            View image = view.findViewById(R.id.close);
            image.setOnClickListener(mRemoveClickListener);
            return view;
        }

        // Playlist change observers
        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            mPlaylist.registerObserver(new PlaylistObserver.Wrapper(observer));
            super.registerDataSetObserver(observer);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            mPlaylist.unregisterObserver(new PlaylistObserver.Wrapper(observer));
            super.unregisterDataSetObserver(observer);
        }
    }
}
