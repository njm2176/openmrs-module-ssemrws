package org.openmrs.module.ssemrws.queries;

import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

@Component
public class GetRecurrenceOfIIT {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public int getRecurrenceOfIIT(String patientUuid) {
		if (patientUuid == null || patientUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid patient UUID");
		}
		
		String qry = "SELECT COUNT(e.date_restarted) " + "FROM ssemr_etl.ssemr_flat_encounter_art_interruption e "
		        + "JOIN openmrs.person p ON e.client_id = p.person_id " + "WHERE p.uuid = :patientUuid "
		        + "AND e.date_restarted IS NOT NULL " + "AND e.encounter_datetime <= :now";
		
		List<Number> results = entityManager.createNativeQuery(qry).setParameter("patientUuid", patientUuid)
		        .setParameter("now", new Date()).getResultList();
		
		return (results.isEmpty() || results.get(0) == null) ? 0 : results.get(0).intValue();
	}
}
