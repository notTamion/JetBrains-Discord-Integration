/*
 * Copyright 2017-2020 Aljoscha Grebe
 * Copyright 2023 Axel JOLY (Azn9) <contact@azn9.dev>
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

package com.almightyalpaca.jetbrains.plugins.discord.plugin.rpc

import com.almightyalpaca.jetbrains.plugins.discord.plugin.DiscordPlugin
import com.almightyalpaca.jetbrains.plugins.discord.plugin.rpc.connection.DiscordConnection
import com.almightyalpaca.jetbrains.plugins.discord.plugin.rpc.connection.DiscordIpcConnection
import com.almightyalpaca.jetbrains.plugins.discord.plugin.utils.DisposableCoroutineScope
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val rpcService: RpcService
    get() = service()

typealias UserCallback = (User?) -> Unit

@Service
class RpcService : DisposableCoroutineScope {
    override val parentJob: Job = SupervisorJob()

    private var _user: User? = null
    val user: User
        get() = _user ?: User.CLYDE

    private var connection: DiscordConnection? = null

    private var lastPresence: RichPresence? = null

    private var connectionChecker: Job? = null

    private val mutex = Mutex()

    private fun checkConnected(): Job = launch {
        delay(20_000)

        mutex.withLock {
            DiscordPlugin.LOG.debug("Checking for running rpc connection")

            val connection = connection ?: return@launch

            if (!connection.running) {
                DiscordPlugin.LOG.debug("Rpc connection not running, reconnecting")

                update(lastPresence, forceReconnect = true)
            } else {
                DiscordPlugin.LOG.debug("Rpc connection is running")

                checkConnected()
            }
        }
    }

    private fun updateUser(user: User?) {
        _user = user
    }

    fun update(presence: RichPresence?, forceUpdate: Boolean = false, forceReconnect: Boolean = false) = launch {
        DiscordPlugin.LOG.debug("Called .update , islocked=${mutex.isLocked}")

        val exceptionHandler = CoroutineExceptionHandler { _, error ->
            when (error) {
                is ProcessCanceledException -> {
                    DiscordPlugin.LOG.warn("PCE while updating presence", error)
                }

                else -> {
                    DiscordPlugin.LOG.warn("Error while updating presence", error)
                }
            }
        }

        mutex.withLock {
            MainScope().launch(exceptionHandler) {
                DiscordPlugin.LOG.debug("Updating presence, forceUpdate=$forceUpdate, forceReconnect=$forceReconnect")

                if (!(forceUpdate || forceReconnect)) {
                    if (lastPresence != null) {
                        if (lastPresence == presence) {
                            return@launch
                        }

                        lastPresence = presence
                    }
                }

                if (presence?.appId == null) { // Stop connection
                    when (presence) {
                        null -> DiscordPlugin.LOG.debug("Presence null, stopping connection")
                        else -> DiscordPlugin.LOG.debug("Presence.appId null, stopping connection")
                    }

                    if (connection != null) {
                        connectionChecker?.cancel()
                        connectionChecker = null
                        connection?.disconnect()
                        connection = null
                    }
                } else {
                    if (forceReconnect || connection?.appId != presence.appId) {
                        when {
                            forceReconnect -> DiscordPlugin.LOG.debug("Forcing reconnect to client")
                            connection == null -> DiscordPlugin.LOG.debug("Connecting to client")
                            else -> DiscordPlugin.LOG.debug("Reconnecting to client due to changed appId")
                        }

                        if (connection != null) {
                            connectionChecker?.cancel()
                            connectionChecker = null
                            connection?.run(Disposer::dispose)
                            connection = null
                        }

                        connection = createConnection(presence.appId).apply {
                            Disposer.register(this@RpcService, this@apply)
                            connect()
                        }

                        connectionChecker = checkConnected()

                    }

                    withTimeoutOrNull(1000) {
                        connection?.send(presence)
                    }
                }
            }
        }
    }

    override fun dispose() {
        runBlocking { update(null) }

        super.dispose()
    }

    private fun createConnection(appId: Long): DiscordConnection {
        return DiscordIpcConnection(appId, ::updateUser)
    }
}
