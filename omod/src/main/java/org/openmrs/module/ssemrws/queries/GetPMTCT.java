package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

@Component
public class GetPMTCT {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public String getPMTCTClient(Patient patient, Date startDate, Date endDate) {
		if (patient == null || patient.getPatientId() == null) {
			throw new IllegalArgumentException("Invalid patient or patient ID");
		}
		
		String qry = "SELECT client_id " + "FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up "
		        + "WHERE client_id = :patientId " + "AND client_pmtct = 'Yes' "
		        + "AND DATE(encounter_datetime) BETWEEN DATE(:startDate) AND DATE(:endDate) " + "LIMIT 1";
		
		List<Object> results = entityManager.createNativeQuery(qry).setParameter("patientId", patient.getPatientId())
		        .setParameter("startDate", startDate).setParameter("endDate", endDate).getResultList();
		
		return results.isEmpty() ? "" : results.get(0).toString();
	}
}
