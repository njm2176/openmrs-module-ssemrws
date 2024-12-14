package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

@Component
public class GetTxCurrQueries {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private final FetchPatientsByIdentifier fetchPatientsByIdentifier;
	
	public GetTxCurrQueries(FetchPatientsByIdentifier fetchPatientsByIdentifier) {
		this.fetchPatientsByIdentifier = fetchPatientsByIdentifier;
	}
	
	public HashSet<Patient> getTxCurr(Date startDate, Date endDate) {
		List<Integer> patientIds = executeTxCurrQuery(startDate, endDate);
		return fetchPatientsByIdentifier.fetchPatientsIds(patientIds);
	}
	
	private List<Integer> executeTxCurrQuery(Date startDate, Date endDate) {
		String baseQuery = "SELECT DISTINCT fp.patient_id " + "FROM openmrs.patient_appointment fp "
		        + "JOIN openmrs.person p ON fp.patient_id = p.person_id "
		        + "JOIN openmrs.obs obs ON obs.person_id = p.person_id "
		        + "WHERE obs.concept_id = (SELECT concept_id FROM openmrs.concept WHERE uuid = :enrollmentUuid) "
		        + "AND obs.value_datetime IS NOT NULL " + "AND (fp.start_date_time >= :currentDate "
		        + "     OR (fp.start_date_time BETWEEN :startDate AND :endDate "
		        + "         AND DATE(fp.start_date_time) >= CURRENT_DATE - INTERVAL 28 DAY))";
		
		Query query = entityManager.createNativeQuery(baseQuery)
		        .setParameter("enrollmentUuid", "73779d67-7e8f-46fe-b723-8879838da5f8")
		        .setParameter("currentDate", new Date()).setParameter("startDate", startDate)
		        .setParameter("endDate", endDate);
		
		return query.getResultList();
	}
}
