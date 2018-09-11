package org.mozilla.scryer.permission

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.*
import org.mockito.ArgumentMatchers.anyBoolean
import org.mozilla.scryer.MainActivity
import kotlin.reflect.KClass

class PermissionFlowTest {

    private lateinit var permissionState: PermissionFlow.PermissionStateProvider
    private lateinit var pageState: PermissionFlow.PageStateProvider

    @Mock
    private lateinit var viewDelegate: PermissionFlow.ViewDelegate

    private var permissions = mutableListOf(false, false)
    private var shouldShowStorageRational = false

    @Captor
    private lateinit var runnableCaptor: ArgumentCaptor<Runnable>

    private lateinit var flow: PermissionFlow
    private var pageStateData = mutableListOf(false, false, false)

    @Before
    fun setUp() {
        permissions = mutableListOf(false, false, false)
        pageStateData = mutableListOf(false, false, false)
        shouldShowStorageRational = false
        permissionState = object : PermissionFlow.PermissionStateProvider {
            override fun isStorageGranted(): Boolean {
                return permissions[0]
            }

            override fun isOverlayGranted(): Boolean {
                return permissions[1]
            }

            override fun shouldShowStorageRational(): Boolean {
                return shouldShowStorageRational
            }
        }

        pageState = object : PermissionFlow.PageStateProvider {

            override fun isWelcomePageShown(): Boolean {
                return pageStateData[0]
            }

            override fun isOverlayPageShown(): Boolean {
                return pageStateData[1]
            }

            override fun isCapturePageShown(): Boolean {
                return pageStateData[2]
            }

            override fun setWelcomePageShown() {
                pageStateData[0] = true
            }

            override fun setOverlayPageShown() {
                pageStateData[1] = true
            }

            override fun setCapturePageShown() {
                pageStateData[2] = true
            }
        }

        MockitoAnnotations.initMocks(this)
        flow = PermissionFlow(permissionState, pageState, viewDelegate)
    }

    @After
    fun tearDown() {
    }

    /**
     * Test ViewDelegate
     */

    @Test
    fun firstLaunch_clickToRequestStorage() {
        // Prepare
        flow.start()
        verifyMethod().showWelcomePage(capture(runnableCaptor), anyBoolean())
        runnableCaptor.value.run()

        // Test: Click to request storage permission
        verifyMethod().requestStoragePermission()
    }

    @Test
    fun denyStorage_clickToRequestAgain() {
        // Setup: Deny permission
        denyStorage(flow, false)
        verifyMethod().showStoragePermissionView(anyBoolean(), capture(runnableCaptor))
        runnableCaptor.value.run()

        // Test: Click action button to request again
        verifyMethod().requestStoragePermission()
    }

    @Test
    fun denyStorageForever_clickToLaunchSetting() {
        // Setup: Deny permission
        denyStorage(flow, true)
        verifyMethod().showStoragePermissionView(anyBoolean(), capture(runnableCaptor))
        runnableCaptor.value.run()

        // Test: Click action button to launch setting
        verifyMethod().launchSystemSettingPage()
    }

    @Test
    fun overlayState_showOverlayPage() {
        // Prepare
        flow.initialState = PermissionFlow.OverlayState(flow)
        flow.start()

        // Test
        verifyMethod().showOverlayPermissionView(any(), any())
    }

    @Test
    fun overlayState_showOverlayPage_yesToRequestOverlayPermission() {
        // Prepare
        flow.initialState = PermissionFlow.OverlayState(flow)
        flow.start()
        verifyMethod().showOverlayPermissionView(capture(runnableCaptor), any())
        runnableCaptor.value.run()

        // Test
        verifyMethod().requestOverlayPermission()
    }

    @Test
    fun overlayState_showOverlayPage_noToFinish() {
        // Prepare
        flow.initialState = PermissionFlow.OverlayState(flow)
        flow.start()
        verifyMethod().showOverlayPermissionView(any(), capture(runnableCaptor))
        runnableCaptor.value.run()

        // Test
        verifyMethod().onOverlayDenied()
        verifyMethod().onPermissionFlowFinish()
    }

    @Test
    fun captureState_showCapturePage() {
        // Prepare
        flow.initialState = PermissionFlow.CaptureState(flow)
        flow.start()
        verifyMethod().showCapturePermissionView(any(), capture(runnableCaptor))
        runnableCaptor.value.run()

        // Test
        verifyMethod().onPermissionFlowFinish()
    }

    @Test
    fun firstLaunch_onPermissionResult_setWelcomePageShown() {
        pageStateData[0] = false
        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf(true))
        flow.start()
        assertTrue(pageState.isWelcomePageShown())

        pageStateData[0] = false
        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf(false))
        flow.start()
        assertTrue(pageState.isWelcomePageShown())

        pageStateData[0] = false
        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf())
        flow.start()
        assertFalse(pageState.isWelcomePageShown())
    }

    /**
     * Test flow state transfer
     */

    @Test
    fun storageNotGranted_transferToStorageState() {
        flow.start()
        assertTrue(flow.state is PermissionFlow.StorageState.FirstTimeRequest)

        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf(false))
        flow.start()
        assertTrue(flow.state is PermissionFlow.StorageState.NonFirstTimeRequest)
    }

    @Test
    fun storageGranted_overlayNotGranted_transferToOverlayState() {
        permissions[0] = true
        pageState.setWelcomePageShown()

        // Test: First time
        flow.start()
        // In case user manually grant storage permission before launching the app
        assertTrue(flow.state is PermissionFlow.OverlayState.FirstTimeRequest)

        // Test: Second time, directly finish the flow
        flow.start()
        assertTrue(flow.state is PermissionFlow.FinishState)
    }

    @Test
    fun storageGranted_overlayGrantedInDefault_transferToCaptureState() {
        permissions[0] = true
        permissions[1] = true
        pageState.setWelcomePageShown()

        // Test: First time
        flow.start()
        assertTrue(flow.state is PermissionFlow.FinishState)

        // Test: Second time, directly finish the flow
        flow.start()
        assertTrue(flow.state is PermissionFlow.FinishState)
    }

    private fun verifyMethod(): PermissionFlow.ViewDelegate {
        return Mockito.verify<PermissionFlow.ViewDelegate>(this.viewDelegate)
    }

    private fun grantStorage(flow: PermissionFlow) {
        permissions[0] = true
        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf(true))
        flow.start()
    }

    private fun denyStorage(flow: PermissionFlow, dontAskAgain: Boolean) {
        permissions[0] = false
        flow.onPermissionResult(MainActivity.REQUEST_CODE_WRITE_EXTERNAL_PERMISSION, booleanArrayOf(false))
        shouldShowStorageRational = !dontAskAgain
        flow.start()
    }

    private fun <T> any(): T {
        Mockito.any<T>()
        return castNull()
    }

    inline fun <reified T : Any> capture(captor: ArgumentCaptor<T>): T {
        return captor.capture() ?: createInstance()
    }

    inline fun <reified T : Any> createInstance(): T {
        return createInstance(T::class)
    }

    fun <T : Any> createInstance(kClass: KClass<T>): T {
        return castNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> castNull(): T = null as T
}