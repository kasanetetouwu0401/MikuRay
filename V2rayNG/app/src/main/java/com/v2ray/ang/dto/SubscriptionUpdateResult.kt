package com.v2ray.ang.dto

/**
 * A single added/deleted profile entry produced while diffing a subscription update,
 * tagged with the subscription it belongs to (so multi-subscription updates can be
 * disambiguated when shown together in a single dialog).
 */
data class ProfileDiffEntry(
    val subscriptionName: String,
    val profileName: String
)

/**
 * Result of subscription update operation
 */
data class SubscriptionUpdateResult(
    val configCount: Int = 0,      // Total configs updated
    val successCount: Int = 0,     // Subscriptions updated successfully
    val failureCount: Int = 0,     // Subscriptions failed to update
    val skipCount: Int = 0,        // Subscriptions skipped (disabled)
    val addedProfiles: List<ProfileDiffEntry> = emptyList(),   // Profiles newly added by this update
    val deletedProfiles: List<ProfileDiffEntry> = emptyList()  // Profiles removed by this update
) {
    /**
     * Combine two results by adding their counts
     */
    operator fun plus(other: SubscriptionUpdateResult): SubscriptionUpdateResult {
        return SubscriptionUpdateResult(
            configCount = this.configCount + other.configCount,
            successCount = this.successCount + other.successCount,
            failureCount = this.failureCount + other.failureCount,
            skipCount = this.skipCount + other.skipCount,
            addedProfiles = this.addedProfiles + other.addedProfiles,
            deletedProfiles = this.deletedProfiles + other.deletedProfiles
        )
    }
}

