package com.bitchat.android.ai.classifier

/**
 * Rule-based message priority classifier using FEMA / ICS keyword matching.
 *
 * Serves as the default fallback when no TFLite model is available, and as
 * the fast first-stage in [CompositeMessageClassifier].
 *
 * Classification is deterministic, requires no model assets, and runs entirely on-device.
 *
 * Each keyword is mapped to the closest [EmergencyType] string so the UI
 * can display a specific badge (e.g. "🏥 MEDICAL · 95%") regardless of the
 * classifier used.
 */
class KeywordMessageClassifier : MessagePriorityClassifier {

    override fun classify(
        messageText: String,
        metadata: Map<String, String>
    ): ClassificationResult {
        val text = messageText.uppercase()

        // ── CRITICAL: imminent life-safety ─────────────────────────────────
        for ((keyword, emergencyType) in CRITICAL_KEYWORDS) {
            if (text.contains(keyword)) {
                return ClassificationResult(
                    priority      = MessagePriority.CRITICAL,
                    confidence    = 0.95f,
                    emergencyType = emergencyType,
                    reasoning     = "Keyword match: \"$keyword\""
                )
            }
        }

        // ── HIGH: urgent operational ────────────────────────────────────────
        for ((keyword, emergencyType) in HIGH_KEYWORDS) {
            if (text.contains(keyword)) {
                return ClassificationResult(
                    priority      = MessagePriority.HIGH,
                    confidence    = 0.85f,
                    emergencyType = emergencyType,
                    reasoning     = "Keyword match: \"$keyword\""
                )
            }
        }

        // ── LOW: clearly non-urgent markers ────────────────────────────────
        val lowHit = LOW_KEYWORDS.firstOrNull { text.contains(it) }
        if (lowHit != null) {
            return ClassificationResult(
                priority   = MessagePriority.NONE,
                confidence = 0.75f,
                reasoning  = "Keyword match: \"$lowHit\""
            )
        }

        return ClassificationResult(
            priority   = MessagePriority.NORMAL,
            confidence = 0.60f,
            reasoning  = "No priority keyword found"
        )
    }

    companion object {
        /**
         * Each entry is (keyword, emergencyType).
         * emergencyType matches the TFLite label strings so both classifiers
         * produce consistent badge labels.
         *
         * Keywords are checked in order — put more specific phrases BEFORE
         * shorter single words to avoid premature matches.
         */

        // ── CRITICAL ───────────────────────────────────────────────────────
        private val CRITICAL_KEYWORDS = listOf(
            // Medical — immediate life threat
            "CARDIAC ARREST"          to "MEDICAL",
            "HEART ATTACK"            to "MEDICAL",
            "NOT BREATHING"           to "MEDICAL",
            "STOPPED BREATHING"       to "MEDICAL",
            "NO PULSE"                to "MEDICAL",
            "UNCONSCIOUS"             to "MEDICAL",
            "UNRESPONSIVE"            to "MEDICAL",
            "MASS CASUALTY"           to "MEDICAL",
            "MCI"                     to "MEDICAL",
            "OVERDOSE"                to "MEDICAL",
            "ANAPHYLAXIS"             to "MEDICAL",
            "SEVERE BLEEDING"         to "MEDICAL",
            "HEAVY BLEEDING"          to "MEDICAL",
            "BLEEDING OUT"            to "MEDICAL",
            "STROKE"                  to "MEDICAL",
            "SEIZURE"                 to "MEDICAL",
            "SOS"                     to "MEDICAL",
            "MAYDAY"                  to "MEDICAL",
            // Entrapment / mobility loss — physically unable to escape (disaster context)
            "CANNOT MOVE"             to "MEDICAL",  // e.g. "I cannot move my legs"
            "CAN NOT MOVE"            to "MEDICAL",
            "CAN'T MOVE"              to "MEDICAL",  // with apostrophe
            "CANT MOVE"               to "MEDICAL",  // apostrophe stripped by some keyboards
            "TRAPPED UNDER"           to "COLLAPSE", // structural entrapment ("trapped under rubble")
            "TRAPPED INSIDE"          to "MEDICAL",  // trapped in a building/vehicle
            "I AM TRAPPED"            to "COLLAPSE",
            "I'M TRAPPED"             to "COLLAPSE",
            "IM TRAPPED"              to "COLLAPSE", // apostrophe stripped
            // Flood — imminent inundation
            "FLASH FLOOD WARNING"     to "FLOOD",
            "DAM BREACH"              to "FLOOD",
            "LEVEE BREACH"            to "FLOOD",
            // Security — immediate threat
            "ACTIVE SHOOTER"          to "SECURITY",
            "SHOTS FIRED"             to "SECURITY",
            "GUNFIRE"                 to "SECURITY",
            "HOSTAGE"                 to "SECURITY",
            "SHELTER IN PLACE"        to "SECURITY",
            "CODE RED"                to "SECURITY",
            "BOMB"                    to "SECURITY",
            "EXPLOSIVE"               to "SECURITY",
            "TERRORIST"               to "SECURITY",
            // Collapse — structural catastrophe (specific phrases only)
            "EXPLOSION"               to "COLLAPSE",
            "BUILDING COLLAPSED"      to "COLLAPSE",
            "STRUCTURE COLLAPSE"      to "COLLAPSE",
            // Person collapse — medical emergency ("she collapsed", "old woman collapsed", etc.)
            "COLLAPSED"               to "MEDICAL",
            // Fire — immediate evacuation
            "IMMEDIATE EVACUATION"    to "FIRE",
            "WILDFIRE APPROACHING"    to "FIRE"
        )

        // ── HIGH ───────────────────────────────────────────────────────────
        private val HIGH_KEYWORDS = listOf(
            // Medical — urgent but not immediately fatal
            "INJURED"                 to "MEDICAL",
            "INJURIES"                to "MEDICAL",
            "BLEEDING"                to "MEDICAL",
            "BLOOD"                   to "MEDICAL",
            "BROKEN BONE"             to "MEDICAL",
            "FRACTURE"                to "MEDICAL",
            "BURN"                    to "MEDICAL",
            "MEDICAL"                 to "MEDICAL",
            "AMBULANCE"               to "MEDICAL",
            "FIRST AID"               to "MEDICAL",
            "CPR"                     to "MEDICAL",
            "TRAUMA"                  to "MEDICAL",
            "HURT"                    to "MEDICAL",
            "PAIN"                    to "MEDICAL",
            "URGENT"                  to "MEDICAL",
            "EMERGENCY"               to "MEDICAL",
            "CAN'T BREATHE"     to "MEDICAL",
            "CANT BREATHE"      to "MEDICAL",
            "CANNOT BREATHE"    to "MEDICAL",
            "HARD TO BREATHE"   to "MEDICAL",
            "DIFFICULTY BREATHING" to "MEDICAL",
            "TROUBLE BREATHING" to "MEDICAL",
            "CHOKING"           to "MEDICAL",
            "SUFFOCATING"       to "MEDICAL",
            // Fire
            "FIRE"                    to "FIRE",
            "WILDFIRE"                to "FIRE",
            "BUILDING ON FIRE"        to "FIRE",
            "HOUSE ON FIRE"           to "FIRE",
            "SMOKE"                   to "FIRE",
            "EVACUATE"                to "FIRE",
            "EVACUATION"              to "FIRE",
            "BURNING"                 to "FIRE",
            // Flood
            "FLOOD"                   to "FLOOD",
            "FLOODING"                to "FLOOD",
            "FLASH FLOOD"             to "FLOOD",
            "RISING WATER"            to "FLOOD",
            "WATER RISING"            to "FLOOD",
            "WATER LEVEL"             to "FLOOD",
            "OVERFLOW"                to "FLOOD",
            "INUNDATION"              to "FLOOD",
            // Collapse / earthquake
            "EARTHQUAKE"              to "COLLAPSE",
            "AFTERSHOCK"              to "COLLAPSE",
            "COLLAPSE"                to "COLLAPSE",
            "BUILDING DAMAGE"         to "COLLAPSE",
            "STRUCTURAL DAMAGE"       to "COLLAPSE",
            "WALL CRACKED"            to "COLLAPSE",
            "ROOF COLLAPSED"          to "COLLAPSE",
            // Security
            "ARMED"                   to "SECURITY",
            "INTRUDER"                to "SECURITY",
            "THREAT"                  to "SECURITY",
            "LOCKDOWN"                to "SECURITY",
            "SUSPICIOUS"              to "SECURITY",
            "ROBBERY"                 to "SECURITY",
            "ATTACK"                  to "SECURITY",
            "VIOLENCE"                to "SECURITY",
            // Weather
            "TORNADO"                 to "WEATHER",
            "HURRICANE"               to "WEATHER",
            "CYCLONE"                 to "WEATHER",
            "TYPHOON"                 to "WEATHER",
            "BLIZZARD"                to "WEATHER",
            "HAILSTORM"               to "WEATHER",
            "THUNDERSTORM"            to "WEATHER",
            "LIGHTNING STRIKE"        to "WEATHER",
            "HEAVY RAIN"              to "WEATHER",
            "HEAVY SNOW"              to "WEATHER",
            "ICE STORM"               to "WEATHER",
            "HIGH WIND"               to "WEATHER",
            "STORM"                   to "WEATHER",
            "SNOWSTORM"               to "WEATHER",
            // Infrastructure
            "GAS LEAK"                to "INFRASTRUCTURE",
            "GAS SMELL"               to "INFRASTRUCTURE",
            "HAZMAT"                  to "INFRASTRUCTURE",
            "CHEMICAL SPILL"          to "INFRASTRUCTURE",
            "POWER OUTAGE"            to "INFRASTRUCTURE",
            "POWER IS OUT"            to "INFRASTRUCTURE",
            "NO ELECTRICITY"          to "INFRASTRUCTURE",
            "NO POWER"                to "INFRASTRUCTURE",
            "ELECTRICITY OUT"         to "INFRASTRUCTURE",
            "ELECTRICITY"             to "INFRASTRUCTURE",
            "BLACKOUT"                to "INFRASTRUCTURE",
            "GRID DOWN"               to "INFRASTRUCTURE",
            "WATER MAIN BREAK"        to "INFRASTRUCTURE",
            "WATER MAIN"              to "INFRASTRUCTURE",
            "PIPE BURST"              to "INFRASTRUCTURE",
            "PIPE BROKEN"             to "INFRASTRUCTURE",
            "TAP WATER"               to "INFRASTRUCTURE",
            "NO WATER"                to "INFRASTRUCTURE",
            "WATER SUPPLY"            to "INFRASTRUCTURE",
            "SEWAGE"                  to "INFRASTRUCTURE",
            "ROAD BLOCKED"            to "INFRASTRUCTURE",
            "ROAD CLOSED"             to "INFRASTRUCTURE",
            "BRIDGE DAMAGED"          to "INFRASTRUCTURE",
            "BRIDGE CLOSED"           to "INFRASTRUCTURE",
            "CRITICAL INFRASTRUCTURE" to "INFRASTRUCTURE",
            "ICS-213"                 to "INFRASTRUCTURE",
            "INCIDENT COMMAND"        to "INFRASTRUCTURE",
            // Missing person
            "MISSING PERSON"          to "MISSING_PERSON",
            "PERSON MISSING"          to "MISSING_PERSON",
            "MISSING CHILD"           to "MISSING_PERSON",
            "LOST CHILD"              to "MISSING_PERSON",
            "CHILD MISSING"           to "MISSING_PERSON",
            "SEARCH AND RESCUE"       to "MISSING_PERSON",
            "MISSING"                 to "MISSING_PERSON",
            "LOST PERSON"             to "MISSING_PERSON",
            "CAN'T FIND"              to "MISSING_PERSON",
            "CANNOT FIND"             to "MISSING_PERSON",
            "CAN NOT FIND"            to "MISSING_PERSON",
            "FIND MY KID"             to "MISSING_PERSON",
            "FIND MY CHILD"           to "MISSING_PERSON",
            "FIND MY SON"             to "MISSING_PERSON",
            "FIND MY DAUGHTER"        to "MISSING_PERSON",
            "WHERE IS MY"             to "MISSING_PERSON",
            "HAVEN'T SEEN"            to "MISSING_PERSON",
            "HAVE NOT SEEN"           to "MISSING_PERSON",
            // Resource requests
            "NEED HELP"               to "RESOURCE_REQUEST",
            "HELP NEEDED"             to "RESOURCE_REQUEST",
            "REQUESTING ASSISTANCE"   to "RESOURCE_REQUEST",
            "REQUEST ASSISTANCE"      to "RESOURCE_REQUEST",
            "NEED FOOD"               to "RESOURCE_REQUEST",
            "NO FOOD"                 to "RESOURCE_REQUEST",
            "HAVE NO FOOD"            to "RESOURCE_REQUEST",
            "OUT OF FOOD"             to "RESOURCE_REQUEST",
            "STARVING"                to "RESOURCE_REQUEST",
            "HUNGRY"                  to "RESOURCE_REQUEST",
            "NEED WATER"              to "RESOURCE_REQUEST",
            "RUNNING OUT OF WATER"    to "RESOURCE_REQUEST",
            "NEED SHELTER"            to "RESOURCE_REQUEST",
            "NO SHELTER"              to "RESOURCE_REQUEST",
            "NOWHERE TO STAY"         to "RESOURCE_REQUEST",
            "NEED MEDICINE"           to "RESOURCE_REQUEST",
            "NO MEDICINE"             to "RESOURCE_REQUEST",
            "NEED SUPPLIES"           to "RESOURCE_REQUEST",
            "RUNNING OUT"             to "RESOURCE_REQUEST",
            "OUT OF SUPPLIES"         to "RESOURCE_REQUEST",
            "SUPPLY SHORTAGE"         to "RESOURCE_REQUEST",
            "FOOD SHORTAGE"           to "RESOURCE_REQUEST",
            "WATER SHORTAGE"          to "RESOURCE_REQUEST",
        )

        // ── LOW: clearly non-urgent ────────────────────────────────────────
        private val LOW_KEYWORDS = listOf(
            "TEST", "TESTING", "PING", "CHECK-IN", "STATUS OK",
            "ALL CLEAR", "ROUTINE", "FYI", "INFO ONLY", "DRILL",
            "EXERCISE", "NO EMERGENCY", "FALSE ALARM"
        )
    }
}
