/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.scryer.permission

import android.content.Context
import android.preference.PreferenceManager
import android.support.v4.app.FragmentActivity
import org.mozilla.scryer.MainActivity

class PermissionFlow(private var permissionState: PermissionStateProvider,
                     private var pageState: PageStateProvider,
                     private val viewDelegate: ViewDelegate) {
    companion object {
        private const val KEY_WELCOME_PAGE_SHOWN = "welcome_page_shown"
        private const val KEY_OVERLAY_PAGE_SHOWN = "overlay_page_shown"
        private const val KEY_CAPTURE_PAGE_SHOWN = "capture_page_shown"

        fun createDefaultPermissionProvider(activity: FragmentActivity?): PermissionStateProvider {
            return object : PermissionFlow.PermissionStateProvider {

                override fun isStorageGranted(): Boolean {
                    return activity?.let {
                        PermissionHelper.hasStoragePermission(it)
                    }?: false
                }

                override fun isOverlayGranted(): Boolean {
                    return activity?.let {
                        PermissionHelper.hasOverlayPermission(it)
                    }?: false
                }

                override fun shouldShowStorageRational(): Boolean {
                    return activity?.let {
                        PermissionHelper.shouldShowStorageRational(it)
                    }?: false
                }
            }
        }

        fun createDefaultPageStateProvider(context: Context?): PageStateProvider {
            return object : PermissionFlow.PageStateProvider {
                private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

                override fun isWelcomePageShown(): Boolean {
                    return prefs.getBoolean(KEY_WELCOME_PAGE_SHOWN, false)
                }

                override fun isOverlayPageShown(): Boolean {
                    return prefs.getBoolean(KEY_OVERLAY_PAGE_SHOWN, false)
                }

                override fun isCapturePageShown(): Boolean {
                    return prefs.getBoolean(KEY_CAPTURE_PAGE_SHOWN, false)
                }

                override fun setWelcomePageShown() {
                    updatePrefs(KEY_WELCOME_PAGE_SHOWN, true)
                }

                override fun setOverlayPageShown() {
                    updatePrefs(KEY_OVERLAY_PAGE_SHOWN, true)
                }

                override fun setCapturePageShown() {
                    updatePrefs(KEY_CAPTURE_PAGE_SHOWN, true)
                }

                private fun updatePrefs(key: String, value: Boolean) {
                    prefs.edit().putBoolean(key, value).apply()
                }
            }
        }
    }

    var initialState: State = StorageState(this)
    var state: State = initialState

    fun start() {
        state = initialState.execute()
    }

    fun isFinished(): Boolean {
        return state is FinishState
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPermissionResult(requestCode: Int, results: BooleanArray) {
        if (results.isEmpty()) {
            return
        }

        when (requestCode) {
            MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION -> {
                pageState.setWelcomePageShown()

                // Force to update the UI state when onPermissionResult() is called after StorageState.execute()
                // and moved to the wrong StorageState. It can be reproduced on Android 6.0 devices.
                if (state is StorageState.FirstTimeWelcome
                        || state is StorageState.FirstTimeRequest) {
                    state = StorageState(this).execute()
                }
            }
        }
    }

    interface ViewDelegate {
        fun showWelcomePage(action: Runnable, withStoragePermission: Boolean)

        fun showStoragePermissionView(isRational: Boolean, action: Runnable)
        fun showOverlayPermissionView(action: Runnable, negativeAction: Runnable)
        fun showCapturePermissionView(action: Runnable, negativeAction: Runnable)

        fun onStorageGranted()
        fun onOverlayGranted()
        fun onOverlayDenied()

        fun onPermissionFlowFinish()

        fun requestStoragePermission()
        fun requestOverlayPermission()

        fun launchSystemSettingPage()
    }

    interface PermissionStateProvider {
        fun isStorageGranted(): Boolean
        fun isOverlayGranted(): Boolean
        fun shouldShowStorageRational(): Boolean
    }

    interface PageStateProvider {
        fun isWelcomePageShown(): Boolean
        fun isOverlayPageShown(): Boolean
        fun isCapturePageShown(): Boolean

        fun setWelcomePageShown()
        fun setOverlayPageShown()
        fun setCapturePageShown()
    }

    interface State {
        fun execute(): State
        fun transfer(state: State): State {
            return state.execute()
        }
    }

    open class StorageState(private val flow: PermissionFlow) : State {
        override fun execute(): State {
            return transfer(when {
                flow.permissionState.isStorageGranted() -> {
                    if (flow.pageState.isWelcomePageShown()) {
                        Granted(flow)
                    } else {
                        FirstTimeWelcome(flow)
                    }
                }
                flow.pageState.isWelcomePageShown() -> NonFirstTimeRequest(flow)
                else -> FirstTimeRequest(flow)
            })
        }

        class Granted(private val flow: PermissionFlow) : StorageState(flow) {
            override fun execute(): State {
                flow.pageState.setWelcomePageShown()
                flow.viewDelegate.onStorageGranted()
                return transfer(OverlayState(flow))
            }
        }

        class FirstTimeRequest(private val flow: PermissionFlow) : StorageState(flow) {
            override fun execute(): State {
                flow.viewDelegate.showWelcomePage(Runnable {
                    flow.viewDelegate.requestStoragePermission()
                }, true)
                return this
            }
        }

        class NonFirstTimeRequest(private val flow: PermissionFlow) : StorageState(flow) {
            override fun execute(): State {
                val shouldShowRational = flow.permissionState.shouldShowStorageRational()

                flow.viewDelegate.showStoragePermissionView(shouldShowRational, Runnable {
                    if (shouldShowRational) {
                        flow.viewDelegate.requestStoragePermission()
                    } else {
                        flow.viewDelegate.launchSystemSettingPage()
                    }
                })
                return this
            }
        }

        class FirstTimeWelcome(private val flow: PermissionFlow) : StorageState(flow) {
            override fun execute(): State {
                flow.viewDelegate.showWelcomePage(Runnable {
                    flow.pageState.setWelcomePageShown()
                    flow.viewDelegate.onStorageGranted()
                    flow.state = transfer(OverlayState(flow))
                }, false)
                return this
            }
        }
    }

    open class OverlayState(private val flow: PermissionFlow) : State {
        override fun execute(): State {
            return transfer(when {
                flow.permissionState.isOverlayGranted() -> Granted(flow)
                flow.pageState.isOverlayPageShown() -> NonFirstTimeRequest(flow)
                else -> FirstTimeRequest(flow)
            })
        }

        class Granted(private val flow: PermissionFlow) : OverlayState(flow) {
            override fun execute(): State {
                flow.viewDelegate.onOverlayGranted()

                return if (flow.pageState.isOverlayPageShown()) {
                    transfer(FinishState(flow))
                } else {
                    flow.pageState.setOverlayPageShown()
                    transfer(CaptureState(flow))
                }
            }
        }

        class FirstTimeRequest(private val flow: PermissionFlow) : OverlayState(flow) {
            override fun execute(): State {
                flow.pageState.setOverlayPageShown()
                flow.viewDelegate.showOverlayPermissionView(Runnable {
                    flow.viewDelegate.requestOverlayPermission()
                }, Runnable {
                    flow.viewDelegate.onOverlayDenied()
                    flow.state = transfer(FinishState(flow))
                })
                return this
            }
        }

        class NonFirstTimeRequest(private val flow: PermissionFlow) : OverlayState(flow) {
            override fun execute(): State {
                flow.viewDelegate.onOverlayDenied()
                return transfer(FinishState(flow))
            }
        }
    }

    open class CaptureState(private val flow: PermissionFlow) : State {
        override fun execute(): State {
            return transfer(if (flow.pageState.isCapturePageShown()) {
                NonFirstTimeRequest(flow)
            } else {
                FirstTimeRequest(flow)
            })
        }

        class FirstTimeRequest(private val flow: PermissionFlow) : CaptureState(flow) {
            override fun execute(): State {
                flow.viewDelegate.showCapturePermissionView(Runnable {}, Runnable {})
                flow.pageState.setCapturePageShown()
                return transfer(FinishState(flow))
            }
        }

        class NonFirstTimeRequest(private val flow: PermissionFlow) : CaptureState(flow) {
            override fun execute(): State {
                return transfer(FinishState(flow))
            }
        }
    }

    class FinishState(private val flow: PermissionFlow) : State {
        override fun execute(): State {
            flow.viewDelegate.onPermissionFlowFinish()
            return this
        }
    }
}
