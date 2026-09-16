package com.indiedev2k.greyfocus

/** What the service is currently seeing; shown on the main screen for debugging. */
object LiveStatus {
    @Volatile var serviceRunning: Boolean = false
    @Volatile var currentPackage: String? = null
    @Volatile var currentSite: String? = null
    @Volatile var grey: Boolean = false

    fun update(packageName: String?, site: String?, grey: Boolean) {
        currentPackage = packageName
        currentSite = site
        this.grey = grey
    }
}
