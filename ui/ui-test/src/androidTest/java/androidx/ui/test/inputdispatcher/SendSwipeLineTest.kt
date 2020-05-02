/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.test.inputdispatcher

import android.view.MotionEvent
import androidx.test.filters.SmallTest
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.util.MotionEventRecorder
import androidx.ui.test.util.assertHasValidEventTimes
import androidx.ui.test.util.isMonotonousBetween
import androidx.ui.test.util.moveEvents
import androidx.ui.test.util.splitsDurationEquallyInto
import androidx.ui.test.util.verify
import androidx.ui.unit.Duration
import androidx.ui.unit.PxPosition
import androidx.ui.unit.inMilliseconds
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.max

private val start = PxPosition(5f.px, 7f.px)
private val end = PxPosition(23f.px, 29f.px)

/**
 * Tests if the [AndroidInputDispatcher.sendSwipe] gesture works when specifying the gesture as a
 * line between two positions
 */
@SmallTest
@RunWith(Parameterized::class)
class SendSwipeLineTest(private val config: TestConfig) {
    data class TestConfig(
        val duration: Duration,
        val eventPeriod: Long
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return listOf(10L, 9L, 11L).flatMap { period ->
                (1L..100L).map { durationMs ->
                    TestConfig(Duration(milliseconds = durationMs), period)
                }
            }
        }
    }

    @get:Rule
    val inputDispatcherRule: TestRule = AndroidInputDispatcher.TestRule(
        disableDispatchInRealTime = true,
        eventPeriodOverride = config.eventPeriod
    )

    private val duration get() = config.duration
    private val eventPeriod = config.eventPeriod

    private val recorder = MotionEventRecorder()
    private val subject = AndroidInputDispatcher(recorder::sendEvent)

    @After
    fun tearDown() {
        recorder.clear()
    }

    @Test
    fun swipeByLine() {
        subject.sendSwipe(start, end, duration)
        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            val expectedMoveEvents = max(1, duration.inMilliseconds() / eventPeriod).toInt()
            assertThat(size).isAtLeast(2 + expectedMoveEvents) // down move+ up

            // Check down and up events
            val durationMs = duration.inMilliseconds()
            first().verify(start, MotionEvent.ACTION_DOWN, 0)
            last().verify(end, MotionEvent.ACTION_UP, durationMs)

            // Check coordinates and timestamps of move events
            moveEvents.isMonotonousBetween(start, end)
            moveEvents.splitsDurationEquallyInto(0L, durationMs, eventPeriod)
        }
    }
}
