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

package com.android.globalsearch.benchmarks;

import android.content.ComponentName;

/**
 * Latency tests for suggestion sources.
 */

/*

To build and run:

mmm vendor/google/apps/GlobalSearch/benchmarks \
&& adb -e install -r $OUT/system/app/GlobalSearchBenchmarks.apk \
&& sleep 10 \
&& adb -e shell am start -a android.intent.action.MAIN \
        -n com.android.globalsearch.benchmarks/.GlobalSearchLatency \
&& adb -e logcat

 */

public class GlobalSearchLatency extends SourceLatency {

    private static final String[] queries = { "", "a", "s", "e", "r", "pub", "sanxjkashasrxae" };

    private static ComponentName GLOBAL_SEARCH_COMPONENT =
            new ComponentName("com.android.globalsearch",
                "com.android.globalsearch.GlobalSearch");

    @Override
    protected void onResume() {
        super.onResume();

        testGlobalSearch();
    }

    private void testGlobalSearch() {
        for (String query : queries) {
            checkLiveSource("GLOBAL", GLOBAL_SEARCH_COMPONENT, query);
        }
    }

}
