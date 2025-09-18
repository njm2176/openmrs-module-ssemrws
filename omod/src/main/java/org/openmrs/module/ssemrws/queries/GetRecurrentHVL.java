package org.openmrs.module.ssemrws.queries;

import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

@Component
public class GetRecurrentHVL {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public int getRecurrentHVLCount(String patientUuid) {
		if (patientUuid == null || patientUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid patient UUID");
		}
		
		String qry = "SELECT COUNT(*) FROM (" + "    SELECT fp.viral_load_value "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "    JOIN ssemr_etl.mamba_dim_person p ON fp.client_id = p.person_id "
		        + "    WHERE p.uuid = :patientUuid AND fp.viral_load_value >= 1000 " + "    UNION ALL "
		        + "    SELECT hvl.repeat_vl_value " + "    FROM ssemr_etl.ssemr_flat_encounter_high_viral_load hvl "
		        + "    JOIN ssemr_etl.mamba_dim_person p ON hvl.client_id = p.person_id "
		        + "    WHERE p.uuid = :patientUuid AND hvl.repeat_vl_value >= 1000 " + ") AS all_high_viral_loads";
		
		Query query = entityManager.createNativeQuery(qry);
		query.setParameter("patientUuid", patientUuid);
		
		Object result = query.getSingleResult();
		
		if (result instanceof Number) {
			return ((Number) result).intValue();
		}
		
		return 0;
	}
}
