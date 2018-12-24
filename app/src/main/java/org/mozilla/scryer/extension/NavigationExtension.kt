package org.mozilla.scryer.extension

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.Navigation

fun NavController.navigateSafely(srcId: Int, actionId: Int, bundle: Bundle) {
    if (currentDestination?.id == srcId) {
        navigate(actionId, bundle)
    }
}

fun androidx.fragment.app.Fragment.getNavController(): NavController? {
    return view?.let {
        Navigation.findNavController(it)
    }
}