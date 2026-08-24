package com.tripcompanion.app.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a coordinate off to whatever navigation app the device already has.
 *
 * The in-app map is for *seeing* the trip (§12); actually driving there is a solved problem the
 * platform owns, so the navigate action leaves the app rather than reinventing turn-by-turn. No
 * network, no key, nothing to keep in `data/` — it is a pure platform Intent.
 *
 * Two intents, tried in order:
 *  - `google.navigation:q=lat,lon` opens turn-by-turn in Google Maps directly.
 *  - `geo:0,0?q=lat,lon(label)` is the generic geo intent every maps app answers, used when Google
 *    Maps is not the one to take it — or is not installed at all.
 *
 * We wrap [Context.startActivity] in try/catch rather than probing with `resolveActivity`, because
 * resolving would need a `<queries>` entry for package visibility (API 30+) and the catch costs
 * nothing: a device with no handler is a value we absorb, not an error worth surfacing.
 */
object ExternalNavigator {

    fun navigateTo(context: Context, latitude: Double, longitude: Double, label: String) {
        val turnByTurn = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$latitude,$longitude")
        )
        try {
            context.startActivity(turnByTurn)
            return
        } catch (_: ActivityNotFoundException) {
            // Google Maps isn't here to take the turn-by-turn scheme — fall through to plain geo:.
        }

        val geo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=$latitude,$longitude(${Uri.encode(label)})")
        )
        try {
            context.startActivity(geo)
        } catch (_: ActivityNotFoundException) {
            // No maps app of any kind. A hand-off we can't complete is not a crash — the map screen
            // stays exactly as it was and the user is none the worse for having tapped.
        }
    }
}
