/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.globalsearch;

import java.util.Collections;
import java.util.List;

/**
 * Holds the data returned by a suggestion source for a single query.
 */
public class SuggestionResult {
    private final SuggestionSource mSource;
    private final List<SuggestionData> mSuggestions;
    private final int mCount;
    private final int mQueryLimit;

    /**
     * @param source The source that the suggestions come from.
     * @param suggestions The suggestions.
     * @param count The total number of suggestions, which may be greater than the suggestions
     *   returned if that was capped for some reason.
     * @param queryLimit The number of results that the source was asked for. If {@code count}
     *        is greater than or equal to {@code queryLimit}, {@code count} is only
     *        a lower bound, not an exact number.
     *
     */
    public SuggestionResult(SuggestionSource source, List<SuggestionData> suggestions, int count,
            int queryLimit) {
        mSource = source;
        mSuggestions = suggestions;
        mCount = count;
        mQueryLimit = queryLimit;
    }

    /**
     * @param source The source that the suggestions come from.
     * @param suggestions The suggestions.
     */
    public SuggestionResult(SuggestionSource source, List<SuggestionData> suggestions) {
        this(source, suggestions, suggestions.size(), suggestions.size());
    }

    /**
     * Can be used when there are no results.
     *
     * @param source The source that the suggestions come from.
     */
    public SuggestionResult(SuggestionSource source) {
        this(source, Collections.<SuggestionData>emptyList(), 0, 0);
    }

    public SuggestionSource getSource() {
        return mSource;
    }

    public List<SuggestionData> getSuggestions() {
        return mSuggestions;
    }

    public int getCount() {
        return mCount;
    }

    public int getQueryLimit() {
        return mQueryLimit;
    }
}
