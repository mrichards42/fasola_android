package org.fasola.fasolaminutes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Class used to observe playlist and playback state.
 */
public class PlaylistObserver extends Playlist.Observer {
    boolean isPlaylistRegistered = false;
    boolean isReceiverRegistered = false;
    BroadcastReceiver mReceiver;
    Context mContext;
    IntentFilter mFilter;
    String mAction;

    /**
     * Sets the IntentFilter for the BroadcastReceiver.
     *
     * @param actions an array of actions to use for the filter;
     *                if left unset, use {@link PlaybackService#BROADCAST_ALL}.
     */
    public void setFilter(String... actions) {
        if (actions.length == 0)
            actions = PlaybackService.BROADCAST_ALL;
        mFilter = new IntentFilter();
        for (String action : actions)
            mFilter.addAction(action);
    }

    /**
     * Sets the IntentFilter for the BroadcastReceiver.
     *
     * @param filter an IntentFilter
     */
    public void setFilter(IntentFilter filter) {
        mFilter = filter;
    }

    /** Registers broadcast and dataset observers. */
    public void registerAll(@NonNull Context context, String... actions) {
        setFilter(actions);
        registerPlaylistObserver();
        registerBroadcastReceiver(context);
    }

    /** Registers only playlist dataset observer. */
    public void registerPlaylistObserver() {
        Playlist.getInstance().registerObserver(this);
        isPlaylistRegistered = true;
    }

    /** Registers only broadcast receiver. */
    public void registerBroadcastReceiver(@NonNull Context context) {
        mContext = context.getApplicationContext();
        if (mReceiver == null)
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mAction = intent.getAction();
                    PlaylistObserver.this.onReceive(context, intent);
                    PlaylistObserver.this.onChanged();
                    mAction = null;
                }
            };
        if (mFilter == null)
            setFilter(PlaybackService.BROADCAST_ALL);
        LocalBroadcastManager.getInstance(context).registerReceiver(mReceiver, mFilter);
        isReceiverRegistered = true;
    }

    /** Unregisters broadcasts and dataset observers. */
    public void unregister() {
        if (isPlaylistRegistered) {
            Playlist.getInstance().unregisterObserver(this);
            isPlaylistRegistered = false;
        }
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
            isReceiverRegistered = false;
        }
    }

    /** Gets the broadcast intent action.
     *
     * @return action or null if this is not a broadcast
     */
    public String getAction() {
        return mAction;
    }

    /** Override to respond to all events. */
    @Override
    public void onChanged() {
    }

    /** Override to handle broadcasts. */
    public void onReceive(Context context, Intent intent) {
    }

    /** Wrapper class to use DatasetObserver as a PlaylistObserver. */
    public static class Wrapper extends Playlist.Observer {
        DataSetObserver mObserver;

        public Wrapper(DataSetObserver observer) {
            mObserver = observer;
        }

        @Override
        public void onChanged() {
            mObserver.onChanged();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Wrapper && mObserver.equals(((Wrapper)o).mObserver);
        }

        @Override
        public int hashCode() {
            return mObserver.hashCode();
        }
    }
}