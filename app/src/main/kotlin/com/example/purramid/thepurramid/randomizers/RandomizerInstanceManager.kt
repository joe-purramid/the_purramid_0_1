package com.example.purramid.thepurramid.randomizers

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap // Thread-safe collection

/**
 * Manages the set of currently active Randomizer instances for the Purramid app.
 * Uses a thread-safe map for managing instance IDs.
 */
object RandomizerInstanceManager {

    private val activeInstances = ConcurrentHashMap<UUID, Boolean>()

    /** Registers an instance as active when its Activity is created. */
    fun registerInstance(instanceId: UUID) {
        activeInstances[instanceId] = true
    }

    /** Unregisters an instance when its Activity is destroyed. */
    fun unregisterInstance(instanceId: UUID) {
        activeInstances.remove(instanceId)
    }

    /** Checks if the given instance ID is the only one currently registered. */
    fun isLastInstance(instanceId: UUID): Boolean {
        // Check if the map contains only this ID, or will be empty after removing it.
        // This assumes unregisterInstance might be called just before this check.
        return activeInstances.size == 1 && activeInstances.containsKey(instanceId)
        // Alternative: Check if count is 0 AFTER unregistering (depends on call order)
        // return activeInstances.isEmpty()
    }

    /** Gets the current count of active instances. */
    fun getActiveInstanceCount(): Int {
        return activeInstances.size
    }

    /** Gets a snapshot of the currently active instance IDs. */
    fun getActiveInstanceIds(): Set<UUID> {
        return activeInstances.keys.toSet() // Return a copy
    }
}