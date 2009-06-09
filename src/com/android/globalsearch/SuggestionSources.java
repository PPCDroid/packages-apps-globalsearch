package com.android.globalsearch;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.server.search.SearchableInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains the list of all enabled suggestion sources. This class is optimized
 * to make {@link #getEnabledSources()} fast.
 */
public class SuggestionSources {

    // set to true to enable the more verbose debug logging for this file
    private static final boolean DBG = false;
    private static final String TAG = "SuggestionSources";

    // Name of the preferences file used to store suggestion source preferences
    public static final String PREFERENCES_NAME = "SuggestionSources";

    // The key for the preference that holds the selected web search source
    public static final String WEB_SEARCH_SOURCE_PREF = "web_search_source";

    // The intent action broadcasted by our settings UI when any of our settings change.
    public static final String ACTION_SETTINGS_CHANGED =
        "com.android.globalsearch.settings_changed";


    private Context mContext;

    private boolean mLoaded;

    // All available suggestion sources.
    private SourceList mSuggestionSources;

    // The web search source to use. This is the source selected in the preferences,
    // or the default source if no source has been selected.
    private SuggestionSource mSelectedWebSearchSource;

    // All enabled sources. This includes all enabled suggestion sources
    // and the the selected web search source.
    private ArrayList<SuggestionSource> mEnabledSources;
    
    // Updates the inclusion of the web search provider.
    private ShowWebSuggestionsSettingChangeObserver mShowWebSuggestionsSettingChangeObserver;

    /**
     *
     * @param context Used for looking up source information etc.
     */
    public SuggestionSources(Context context) {
        mContext = context;
        mLoaded = false;
    }

    /**
     * Gets all suggestion sources. This does not include any web search sources.
     *
     * @return A list of suggestion sources, including sources that are not enabled.
     *         Callers must not modify the returned list.
     */
    public synchronized Collection<SuggestionSource> getSuggestionSources() {
        if (!mLoaded) {
            Log.w(TAG, "getSuggestionSources() called, but sources not loaded.");
            return Collections.<SuggestionSource>emptyList();
        }
        return mSuggestionSources.values();
    }

    public synchronized SuggestionSource getSourceByComponentName(ComponentName componentName) {
        SuggestionSource source = mSuggestionSources.get(componentName);
        
        // If the source was not found, back off to check the web source in case it's that.
        if (source == null) {
            if (mSelectedWebSearchSource != null &&
                    mSelectedWebSearchSource.getComponentName().equals(componentName)) {
                source = mSelectedWebSearchSource;
            }
        }
        return source;
    }

    /**
     * Gets all sources that should be used to get suggestions.
     *
     * @return All enabled suggestion sources and the selected web search source.
     *         Callers must not modify the returned list.
     */
    public synchronized List<SuggestionSource> getEnabledSources() {
        if (!mLoaded) {
            Log.w(TAG, "getEnabledSources() called, but sources not loaded.");
            return Collections.<SuggestionSource>emptyList();
        }
        return mEnabledSources;
    }

    /**
     * Checks whether a suggestion source is enabled by default.
     */
    public boolean isSourceDefaultEnabled(SuggestionSource source) {
        return true;  // TODO: get from source?
    }

    /**
     * Returns the web search source set in the preferences, or the default source
     * if no web search source has been selected.
     *
     * @return <code>null</code> only if there is no web search source available.
     */
    public synchronized SuggestionSource getSelectedWebSearchSource() {
        if (!mLoaded) {
            Log.w(TAG, "getSelectedWebSearchSource() called, but sources not loaded.");
            return null;
        }
        return mSelectedWebSearchSource;
    }

    /**
     * Gets the search preferences.
     */
    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Gets the preference key of the preference for whether the given source
     * is enabled. The preference is stored in the {@link #PREFERENCES_NAME}
     * preferences file.
     */
    public String getSourceEnabledPreference(SuggestionSource source) {
        return "enable_source_" + source.getComponentName().flattenToString();
    }

    // Broadcast receiver for package change notifications
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED.equals(action)
                    || SuggestionSources.ACTION_SETTINGS_CHANGED.equals(action)) {
                // TODO: Instead of rebuilding the whole list on every change,
                // just add, remove or update the application that has changed.
                // Adding and updating seem tricky, since I can't see an easy way to list the
                // launchable activities in a given package.
                updateSources();
            }
        }
    };

    /**
     * After calling, clients must call {@link #close()} when done with this object.
     */
    public synchronized void load() {
        if (mLoaded) {
            Log.w(TAG, "Already loaded, ignoring call to load().");
            return;
        }

        // Listen for searchables changes.
        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED));

        // Listen for search preference changes.
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(ACTION_SETTINGS_CHANGED));
        
        mShowWebSuggestionsSettingChangeObserver = new ShowWebSuggestionsSettingChangeObserver();
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SHOW_WEB_SUGGESTIONS),
                true,
                mShowWebSuggestionsSettingChangeObserver);

        // update list of sources
        updateSources();
        mLoaded = true;
    }

    /**
     * Releases all resources used by this object. It is possible to call
     * {@link #load()} again after calling this method.
     */
    public synchronized void close() {
        if (!mLoaded) {
            Log.w(TAG, "Not loaded, ignoring call to close().");
            return;
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.getContentResolver().unregisterContentObserver(
                mShowWebSuggestionsSettingChangeObserver);

        mSuggestionSources = null;
        mSelectedWebSearchSource = null;
        mEnabledSources = null;
        mLoaded = false;
    }

    /**
     * Loads the list of suggestion sources. This method is protected so that
     * it can be called efficiently from inner classes.
     */
    protected synchronized void updateSources() {
        mSuggestionSources = new SourceList();
        addExternalSources();

        // TODO: make final decision about music
        addSuggestionSource(MusicSuggestionSource.create(mContext));

        updateEnabledSources();
    }

    private void addExternalSources()  {
        for (SearchableInfo searchable : SearchManager.getSearchablesInGlobalSearch()) {
            SuggestionSource source = new SearchableSuggestionSource(mContext, searchable);
            addSuggestionSource(source);
        }
    }

    private void addSuggestionSource(SuggestionSource source) {
        if (DBG) Log.d(TAG, "Adding source: " + source);
        SuggestionSource old = mSuggestionSources.put(source);
        if (old != null) {
            Log.w(TAG, "Replaced source " + old + " for " + source.getComponentName());
        }
    }

    /**
     * Updates the internal information about which sources are enabled. This must be called
     * whenever the search preferences have been changed.
     */
    private void updateEnabledSources() {
        // non-web sources
        SharedPreferences sharedPreferences = getSharedPreferences();
        ArrayList<SuggestionSource> enabledSources = new ArrayList<SuggestionSource>();
        for (SuggestionSource source : mSuggestionSources.values()) {
            boolean defaultEnabled = isSourceDefaultEnabled(source);
            String sourceEnabledPref = getSourceEnabledPreference(source);
            if (sharedPreferences.getBoolean(sourceEnabledPref, defaultEnabled)) {
                if (DBG) Log.d(TAG, "Adding enabled source " + source);
                enabledSources.add(source);
            }
        }

        // Preferred web source.
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SHOW_WEB_SUGGESTIONS,
                1 /* default on until user actually changes it */) == 1) {
            mSelectedWebSearchSource = null;
            SearchableInfo webSearchable = SearchManager.getDefaultSearchableForWebSearch();
            if (webSearchable != null) {
                if (DBG) Log.d(TAG, "Adding web source " + webSearchable.getSearchActivity());
                mSelectedWebSearchSource = SearchableSuggestionSource.create(
                        mContext, webSearchable.getSearchActivity());
                if (mSelectedWebSearchSource != null) {
                    enabledSources.add(mSelectedWebSearchSource);
                }
            }
        }

        mEnabledSources = enabledSources;
    }

    /**
     * This works like a map from ComponentName to SuggestionSource,
     * but supports a zero-allocation method for listing all the sources.
     */
    private static class SourceList {

        private HashMap<ComponentName,SuggestionSource> mSourcesByComponent;
        private ArrayList<SuggestionSource> mSources;

        public SourceList() {
            mSourcesByComponent = new HashMap<ComponentName,SuggestionSource>();
            mSources = new ArrayList<SuggestionSource>();
        }

        public SuggestionSource get(ComponentName componentName) {
            return mSourcesByComponent.get(componentName);
        }

        /**
         * Adds a source. Replaces any previous source with the same component name.
         *
         * @return The previous source that was replaced, if any.
         */
        public SuggestionSource put(SuggestionSource source) {
            if (source == null) {
                return null;
            }
            SuggestionSource old = mSourcesByComponent.put(source.getComponentName(), source);
            if (old != null) {
                // linear search is ok here, since addSource() is only called when the
                // list of sources is updated, which is infrequent. Also, collisions would only
                // happen if there are two sources with the same component name, which should
                // only happen as long as we have hard-coded sources.
                mSources.remove(old);
            }
            mSources.add(source);
            return old;
        }

        /**
         * Gets the suggestion sources.
         */
        public ArrayList<SuggestionSource> values() {
            return mSources;
        }

        /**
         * Checks whether the list is empty.
         */
        public boolean isEmpty() {
            return mSources.isEmpty();
        }
    }
    
    /**
     * ContentObserver which updates the list of enabled sources to include or exclude
     * the web search provider depending on the state of the
     * {@link Settings.System#SHOW_WEB_SUGGESTIONS} setting.
     */
    private class ShowWebSuggestionsSettingChangeObserver extends ContentObserver {
        public ShowWebSuggestionsSettingChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            updateEnabledSources();
        }
    }
}
