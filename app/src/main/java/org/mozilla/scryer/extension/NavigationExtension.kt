package org.mozilla.scryer.extension

import android.os.Bundle
import androidx.navigation.NavController

fun NavController.navigateSafely(srcId: Int, actionId: Int, bundle: Bundle) {
    if (currentDestination?.id == srcId) {
        navigate(actionId, bundle)
    }
}
