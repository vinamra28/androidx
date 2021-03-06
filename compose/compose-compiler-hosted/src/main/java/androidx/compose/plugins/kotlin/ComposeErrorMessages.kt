/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.plugins.kotlin

import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.Renderers

class ComposeErrorMessages : DefaultErrorMessages.Extension {
    private val MAP =
        DiagnosticFactoryToRendererMap(
            "Compose"
        )
    override fun getMap() = MAP

    init {
        MAP.put(
            ComposeErrors.COMPOSABLE_INVOCATION,
            "@Composable invocations can only happen from the context of a @Composable function"
        )

        MAP.put(
            ComposeErrors.COMPOSABLE_EXPECTED,
            "Functions which invoke @Composable functions must be marked with the @Composable " +
                    "annotation"
        )

        MAP.put(
            ComposeErrors.CAPTURED_COMPOSABLE_INVOCATION,
            "Composable calls are not allowed inside the {0} parameter of {1}",
            Renderers.NAME,
            Renderers.COMPACT
        )

        MAP.put(
            ComposeErrors.COMPOSABLE_PROPERTY_BACKING_FIELD,
            "Composable properties are not able to have backing fields"
        )

        MAP.put(
            ComposeErrors.COMPOSABLE_VAR,
            "Composable properties are not able to have backing fields"
        )
        MAP.put(
            ComposeErrors.COMPOSABLE_SUSPEND_FUN,
            "Composable properties are not able to have backing fields"
        )
        MAP.put(
            ComposeErrors.ILLEGAL_ASSIGN_TO_UNIONTYPE,
            "Value of type {0} can't be assigned to union type {1}.",
            Renderers.RENDER_COLLECTION_OF_TYPES,
            Renderers.RENDER_COLLECTION_OF_TYPES
        )
        MAP.put(
            ComposeErrors.ILLEGAL_TRY_CATCH_AROUND_COMPOSABLE,
            "Try catch is not supported around composable function invocations."
        )
    }
}