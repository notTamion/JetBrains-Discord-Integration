/*
 * Copyright 2017-2020 Aljoscha Grebe
 * Copyright 2023-2024 Axel JOLY (Azn9) <contact@azn9.dev>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.azn9.plugins.discord.icons.matcher

sealed class Matcher {
    abstract fun matches(field: String): Boolean

    sealed class Text(private val strings: Set<String>, private val matcher: String.(String) -> Boolean) : dev.azn9.plugins.discord.icons.matcher.Matcher() {
        override fun matches(field: String) = strings.any { s -> field.matcher(s) }

        class StartsWith(texts: Set<String>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Text(texts, { s -> startsWith(s, true) })

        class EndsWith(texts: Set<String>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Text(texts, { s -> endsWith(s, true) })

        class Contains(texts: Set<String>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Text(texts, { s -> contains(s, true) })

        class Equals(texts: Set<String>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Text(texts, { s -> equals(s, true) })

        class RegEx(expressions: Set<String>) : dev.azn9.plugins.discord.icons.matcher.Matcher() {
            private val expressions: Collection<Regex> = expressions.map { e -> e.toRegex(RegexOption.IGNORE_CASE) }
            override fun matches(field: String) = expressions.any { e -> e.containsMatchIn(field) }
        }
    }

    sealed class Combining(
        private val matchers: Set<dev.azn9.plugins.discord.icons.matcher.Matcher>,
        private val matcher: Set<dev.azn9.plugins.discord.icons.matcher.Matcher>.(String) -> Boolean
    ) : dev.azn9.plugins.discord.icons.matcher.Matcher() {
        override fun matches(field: String) = matchers.matcher(field)

        class All(val matchers: Set<dev.azn9.plugins.discord.icons.matcher.Matcher>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Combining(matchers, { s -> all { m -> m.matches(s) } })

        class Any(val matchers: Set<dev.azn9.plugins.discord.icons.matcher.Matcher>) : dev.azn9.plugins.discord.icons.matcher.Matcher.Combining(matchers, { s -> any { m -> m.matches(s) } })
    }

    enum class Target(val id: String) {
        PATH("path"),
        NAME("name"),
        BASENAME("basename"),
        EXTENSION("extension");
        // CONTENT("content") // TODO: implement content/magic byte matching
        // EDITOR("editor") // TODO: implement matching the editor type

        interface Provider {
            fun getField(target: dev.azn9.plugins.discord.icons.matcher.Matcher.Target): Collection<String>
        }
    }
}
