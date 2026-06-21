package com.movit.feature.shell

import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
internal fun showIosBillingAlert(message: String) {
    var top = iosTopViewController() ?: return
    val alert = UIAlertController.alertControllerWithTitle(
        title = "Movit",
        message = message,
        preferredStyle = UIAlertControllerStyleAlert,
    )
    alert.addAction(
        UIAlertAction.actionWithTitle(
            title = "OK",
            style = UIAlertActionStyleDefault,
            handler = null,
        ),
    )
    top.presentViewController(alert, animated = true, completion = null)
}
