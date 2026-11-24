package mw.phoneguard.app.services

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

data class SingleSimInfo(
    val subId: String,
    val slotIndex: Int,
    val carrierName: String,
    val countryIso: String,
    val mccMnc: String,
    val entries: Set<String>
)

data class SimDetails(
    val operatorName: String,
    val countryIso: String,
    val operatorCode: String,
    val subscriptionId: String,
    val codes: Set<String>,
    val operators: Set<String>,
    val countries: Set<String>,
    val subscriptions: Set<String>,
    val simList: List<SingleSimInfo> = emptyList()
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
        val simList = mutableListOf<SingleSimInfo>()

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
                            val singleEntries = mutableSetOf<String>()

                            val opName = sub.carrierName?.toString().orEmpty()
                            if (opName.isNotBlank()) {
                                operators.add(opName)
                                singleEntries.add("op:$opName")
                            }

                            val cIso = sub.countryIso.orEmpty()
                            if (cIso.isNotBlank()) {
                                countries.add(cIso)
                                singleEntries.add("country:$cIso")
                            }

                            val mccMnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "${sub.mccString.orEmpty()}${sub.mncString.orEmpty()}"
                            } else ""
                            if (mccMnc.isNotBlank()) {
                                codes.add(mccMnc)
                                singleEntries.add("code:$mccMnc")
                            }

                            // 1. Subscription ID (Unique integer in OS telephony DB per physical SIM)
                            val subIdStr = sub.subscriptionId.toString()
                            if (subIdStr.isNotBlank() && subIdStr != "-1") {
                                subscriptions.add("subId:$subIdStr")
                                singleEntries.add("subId:$subIdStr")
                            }

                            // 2. Physical SIM card ID / string via safe reflection (Android 9+)
                            try {
                                val getCardId = sub.javaClass.getMethod("getCardId")
                                val cardId = getCardId.invoke(sub)
                                if (cardId != null && cardId != -1) {
                                    subscriptions.add("cardId:$cardId")
                                    singleEntries.add("cardId:$cardId")
                                }
                            } catch (_: Exception) {}

                            try {
                                val getCardString = sub.javaClass.getMethod("getCardString")
                                val cardStr = getCardString.invoke(sub) as? String
                                if (!cardStr.isNullOrBlank()) {
                                    subscriptions.add("cardStr:$cardStr")
                                    singleEntries.add("cardStr:$cardStr")
                                }
                            } catch (_: Exception) {}

                            // 3. Carrier ID
                            val carrier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) sub.carrierId else sub.simSlotIndex
                            subscriptions.add("sub:${sub.subscriptionId}:$carrier")
                            singleEntries.add("sub:${sub.subscriptionId}:$carrier")

                            // 4. ICCID if accessible
                            val iccId = sub.iccId.orEmpty()
                            if (iccId.isNotBlank()) {
                                subscriptions.add("iccid:$iccId")
                                singleEntries.add("iccid:$iccId")
                            }

                            simList.add(
                                SingleSimInfo(
                                    subId = subIdStr,
                                    slotIndex = sub.simSlotIndex,
                                    carrierName = opName,
                                    countryIso = cIso,
                                    mccMnc = mccMnc,
                                    entries = singleEntries
                                )
                            )
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
            subscriptions = subscriptions,
            simList = simList
        )
    }
}
