package org.mozilla.scryer.extension

import android.os.Bundle
import android.support.v4.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation

fun NavController.navigateSafely(srcId: Int, actionId: Int, bundle: Bundle) {
    if (currentDestination?.id == srcId) {
        navigate(actionId, bundle)
    }
}

fun Fragment.getNavController(): NavController? {
    return view?.let {
        Navigation.findNavController(it)
    }
}