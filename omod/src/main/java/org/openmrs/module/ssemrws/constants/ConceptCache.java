package org.openmrs.module.ssemrws.constants;

import org.openmrs.Concept;
import org.openmrs.api.context.Context;

import java.util.concurrent.ConcurrentHashMap;

public class ConceptCache {
	
	private static final long CACHE_TTL_MS = 3600000;
	
	private static final ConcurrentHashMap<String, CacheEntry> conceptCache = new ConcurrentHashMap<>();
	
	// Inner class to store the concept and its cache time
	private static class CacheEntry {
		
		Concept concept;
		
		long cacheTime;
		
		CacheEntry(Concept concept) {
			this.concept = concept;
			this.cacheTime = System.currentTimeMillis();
		}
	}
	
	// Method to get a concept from the cache or fetch from DB if not present or
	// expired
	public static Concept getCachedConcept(String conceptUuid) {
		CacheEntry entry = conceptCache.get(conceptUuid);
		
		// Check if the entry is cached and still valid
		if (entry != null && (System.currentTimeMillis() - entry.cacheTime) < CACHE_TTL_MS) {
			return entry.concept;
		}
		
		// If not cached or expired, fetch it from the database and cache it
		Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
		if (concept != null) {
			conceptCache.put(conceptUuid, new CacheEntry(concept));
		}
		
		return concept;
	}
}
