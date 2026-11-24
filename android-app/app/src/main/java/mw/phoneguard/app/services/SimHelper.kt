package mw.phoneguard.app.services

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

data class SimDetails(
    val operatorName: String,
    val countryIso: String,
    val operatorCode: String,
    val subscriptionId: String,
    val codes: Set<String>,
    val operators: Set<String>,
    val countries: Set<String>,
    val subscriptions: Set<String>
) {
    fun isLoaded(): Boolean = codes.isNotEmpty() || subscriptions.isNotEmpty() || operators.isNotEmpty()
}

object SimHelper {
    private const val TAG = "SimHelper"

    fun extractSimDetails(context: Context): SimDetails {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val operatorName = tm?.simOperatorName ?: ""
        val countryIso = tm?.simCountryIso ?: ""
        val operatorCode = tm?.simOperator ?: ""

        val codes = mutableSetOf<String>()
        val operators = mutableSetOf<String>()
        val countries = mutableSetOf<String>()
        val subscriptions = mutableSetOf<String>()

        if (operatorCode.isNotBlank()) codes.add(operatorCode)
        if (operatorName.isNotBlank()) operators.add(operatorName)
        if (countryIso.isNotBlank()) countries.add(countryIso)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                if (sm != null) {
                    @Suppress("MissingPermission")
                    val activeList = sm.activeSubscriptionInfoList
                    if (!activeList.isNullOrEmpty()) {
                        for (sub in activeList) {
                            if (!sub.carrierName.isNullOrBlank()) operators.add(sub.carrierName.toString())
                            if (!sub.countryIso.isNullOrBlank()) countries.add(sub.countryIso.toString())

                            val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "${sub.mccString.orEmpty()}${sub.mncString.orEmpty()}"
                            } else ""
                            if (mccMnc.isNotBlank()) codes.add(mccMnc)

                            val carrier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                            subscriptions.add("${sub.subscriptionId}:$carrier")
                            val iccId = sub.iccId.orEmpty()
                            if (iccId.isNotBlank()) {
                                subscriptions.add(iccId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubscriptionManager query error: ${e.message}")
        }

        val subscriptionIdStr = subscriptions.joinToString(",")

        return SimDetails(
            operatorName = operatorName,
            countryIso = countryIso,
            operatorCode = operatorCode,
            subscriptionId = subscriptionIdStr,
            codes = codes,
            operators = operators,
            countries = countries,
            subscriptions = subscriptions
        )
    }
}
