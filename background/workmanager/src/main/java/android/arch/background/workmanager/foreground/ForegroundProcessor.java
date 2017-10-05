/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.arch.background.workmanager.foreground;

import android.arch.background.workmanager.Processor;
import android.arch.background.workmanager.Scheduler;
import android.arch.background.workmanager.WorkDatabase;
import android.arch.background.workmanager.model.WorkSpec;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A {@link Processor} that handles execution when the app is in the foreground.
 */

public class ForegroundProcessor extends Processor
        implements Observer<List<WorkSpec>>, LifecycleObserver {
    private static final String TAG = "ForegroundProcessor";
    private LifecycleOwner mLifecycleOwner;

    public ForegroundProcessor(
            Context appContext,
            WorkDatabase workDatabase,
            Scheduler scheduler,
            LifecycleOwner lifecycleOwner) {
        super(appContext, workDatabase, scheduler);
        mLifecycleOwner = lifecycleOwner;
        mLifecycleOwner.getLifecycle().addObserver(this);
        mWorkDatabase.workSpecDao().getEnqueuedWorkSpecs().observe(mLifecycleOwner, this);
    }

    @Override
    public ScheduledExecutorService createExecutorService() {
        // TODO(sumir): Be more intelligent about this.
        return Executors.newScheduledThreadPool(4);
    }

    @Override
    public boolean isActive() {
        return mLifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    @Override
    public void onChanged(@NonNull List<WorkSpec> workSpecs) {
        // TODO(sumir): Optimize this.  Also, do we need to worry about items *removed* from the
        // list or can we safely ignore them as we are doing right now?
        // Note that this query currently gets triggered when items are REMOVED from the runnable
        // status as well.
        Log.d(TAG, "Enqueued WorkSpecs updated. Size : " + workSpecs.size());
        for (WorkSpec workSpec : workSpecs) {
            if (!mEnqueuedWorkMap.containsKey(workSpec.getId())) {
                process(workSpec.getId(), workSpec.getInitialDelay());
            }
        }
    }

    /**
     * Called when the process lifecycle is considered stopped.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onLifecycleStop() {
        Log.d(TAG, "onLifecycleStop");
        Iterator<Map.Entry<String, Future<?>>> it = mEnqueuedWorkMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Future<?>> entry = it.next();
            if (entry.getValue().cancel(false)) {
                it.remove();
                Log.d(TAG, "Canceled " + entry.getKey());
            } else {
                Log.d(TAG, "Cannot cancel " + entry.getKey());
            }
        }
    }
}
