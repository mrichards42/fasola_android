/*
 * This file is part of FaSoLa Minutes for Android.
 * Copyright (c) 2016 Mike Richards. All rights reserved.
 */

package org.fasola.fasolaminutes;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.InflateException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;

import com.astuetz.PagerSlidingTabStrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  A base class for an activity with tabs.
 *  Use {@link android.support.v4.view.ViewPager} in the layout file, and define tabs using
 *  {@code <fragment android:title="label" android:name="FragmentClass">}
 */
public abstract class SimpleTabActivity extends BaseActivity {
    /** PagerAdapter for ViewPager */
    SimplePagerAdapter mPagerAdapter;

    /** ViewPager (defined in layout) */
    ViewPager mViewPager;

    /** Optional Tab Strip header */
    PagerSlidingTabStrip mTabStrip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getLayoutInflater().setFactory2(this); // Use our onCreateView function
        super.onCreate(savedInstanceState);
        mPagerAdapter = new SimplePagerAdapter(getSupportFragmentManager());
    }

    /**
     * Setup ViewPager and PagerSlidingTabStrip.
     * Remove temporary ViewStubs (=tabs) from ViewPager.
     */
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        // Setup tabs
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.removeAllViews(); // Remove ViewStubs (added in custom onCreateView)
        mViewPager.setAdapter(mPagerAdapter);
        mPagerAdapter.notifyDataSetChanged();
        // Setup PagerSlidingTabStrip (if we have one)
        mTabStrip = (PagerSlidingTabStrip) findViewById(R.id.tab_strip);
        if (mTabStrip != null)
            mTabStrip.setViewPager(mViewPager);
        // Setup page change listener
        if (mTabStrip != null)
            mTabStrip.setOnPageChangeListener(mOwnPageChangeListener);
        else
            mViewPager.setOnPageChangeListener(mOwnPageChangeListener);
    }

    public Fragment getFragmentByPosition(int index) {
        String tag = "android:switcher:" + mViewPager.getId() + ":" + index;
        return getSupportFragmentManager().findFragmentByTag(tag);
    }

    public Fragment getCurrentFragment() {
        return getFragmentByPosition(mViewPager.getCurrentItem());
    }

    /**
     * Interface for fragments to respond to paging events.
     */
    public interface FragmentPagerListener {
        void onPageFocused();
        void onPageBlurred();
    }

    ViewPager.OnPageChangeListener mPageChangeListener = null;

    public void setOnPageChangeListener(ViewPager.OnPageChangeListener listener) {
        mPageChangeListener = listener;
    }

    /**
     * Custom OnPageChangeListener that handles {@link FragmentPagerListener} events.
     * All events pass through to listener set with {@link #setOnPageChangeListener}
     */
    ViewPager.OnPageChangeListener mOwnPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (mPageChangeListener != null)
                mPageChangeListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
        }

        // Save old position for onPageBlurred
        int lastPos = 0;

        /**
         * Calls {@link FragmentPagerListener#onPageFocused()}
         * and {@link FragmentPagerListener#onPageBlurred()}
         */
        @Override
        public void onPageSelected(int position) {
            Fragment from = getFragmentByPosition(lastPos);
            if (from instanceof FragmentPagerListener)
                ((FragmentPagerListener)from).onPageFocused();
            Fragment to = getFragmentByPosition(position);
            if (to instanceof FragmentPagerListener)
                ((FragmentPagerListener)to).onPageBlurred();
            lastPos = position;
            if (mPageChangeListener != null)
                mPageChangeListener.onPageSelected(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (mPageChangeListener != null)
                mPageChangeListener.onPageScrollStateChanged(state);
        }
    };

    // Call setMenuVisibility on pager fragments when the drawer is opened
    @Override
    public void onDrawerOpened(View drawerView) {
        Fragment fragment = getCurrentFragment();
        if (fragment != null)
            fragment.setMenuVisibility(false);
        super.onDrawerOpened(drawerView);
    }

    @Override
    public void onDrawerClosed(View drawerView) {
        Fragment fragment = getCurrentFragment();
        if (fragment != null)
            fragment.setMenuVisibility(true);
        super.onDrawerClosed(drawerView);
    }

    /**
     * Override to initialize a new fragment.  Defaults to setting Activity bundle args as extras.
     * @param fragment The new fragment.
     */
    public void onNewFragment(Fragment fragment) {
        fragment.setArguments(getIntent().getExtras());
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to one of the tabs.
     */
    protected class SimplePagerAdapter extends FragmentPagerAdapter {
        public List<Pair<String, Class<? extends Fragment>>> mTabs;

        public SimplePagerAdapter(FragmentManager fm) {
            super(fm);
            mTabs = new ArrayList<>();
        }

        public void addTab(String label, Class<? extends Fragment> fragmentClass) {
            mTabs.add(new Pair<String, Class<? extends Fragment>>(label, fragmentClass));
        }

        @Override
        public Fragment getItem(int position) {
            try {
                Fragment fragment = mTabs.get(position).second.newInstance();
                onNewFragment(fragment);
                return fragment;
            } catch (IndexOutOfBoundsException|InstantiationException|IllegalAccessException ex) {
                return null;
            }
        }

        @Override
        public int getCount() {
            return mTabs.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            try {
                return mTabs.get(position).first;
            } catch (IndexOutOfBoundsException ex) {
                return null;
            }
        }

        public int getFragmentIndex(Class<? extends Fragment> fragmentClass) {
            for (int i = 0; i < mTabs.size(); i++)
                if (mTabs.get(i).second == fragmentClass)
                    return i;
            return -1;
        }
    }

    /**
     * Use fragment tag to add Tabs to the ViewPager.
     */
    @Override
    @Nullable
    public View onCreateView(View parent, String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        if (name.equals("fragment") && parent instanceof ViewPager) {
            String androidNamespace = "http://schemas.android.com/apk/res/android";
            String fname = attrs.getAttributeValue(androidNamespace, "name");
            int labelId = attrs.getAttributeResourceValue(androidNamespace, "title", 0);
            String label = labelId != 0
                    ? getResources().getString(labelId)
                    : attrs.getAttributeValue(androidNamespace, "title");
            try {
                mPagerAdapter.addTab(label, (Class<? extends Fragment>) Class.forName(fname));
            }
            catch (ClassNotFoundException e) {
                throw new InflateException(attrs.getPositionDescription()
                        + ": Invalid class: " + fname);
            }
            return new ViewStub(context);
        }
        return super.onCreateView(name, context, attrs);
    }

    // Help
    // TODO:adding the help menu item in the activity instead of the fragment is
    // pretty dumb  -- if one fragment wants a help item but another doesn't, the help
    // item will exist across the whole activity.  Could fix this by creating a FragmentDelegate
    // or something (since FragmentMixin isn't possible) so this is shared between subclasses?
    Map<Class<? extends Fragment>, Integer> mFragmentHelpResources = new HashMap<>();

    public void setHelpResource(Fragment fragment, int id) {
        mFragmentHelpResources.put(fragment.getClass(), id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);
        // Don't add an extra help menu if we already have one
        if (mHelpResourceId == -1 && !mFragmentHelpResources.isEmpty()) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_help, menu);
        }
        return ret;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_help && getVisibleDrawer() == null) {
            Integer id = mFragmentHelpResources.get(getCurrentFragment().getClass());
            if (id != null)
                return HelpActivity.start(this, id);
        }
        return super.onOptionsItemSelected(item);
    }
}
