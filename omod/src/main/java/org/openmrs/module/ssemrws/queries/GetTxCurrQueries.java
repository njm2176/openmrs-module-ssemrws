package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.AllConcepts;
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
	
	public HashSet<Patient> getTxCurr(Date endDate) {
		List<Integer> patientIds = executeTxCurrQuery(endDate);
		return fetchPatientsByIdentifier.fetchPatientsIds(patientIds);
	}
	
	public List<Integer> executeTxCurrQuery(Date endDate) {
		String sql = "SELECT DISTINCT p.patient_id " + "FROM openmrs.patient_appointment p " + "JOIN ( "
		        + "    SELECT client_id, art_start_date "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_personal_family_tx_history " + "    UNION "
		        + "    SELECT client_id, art_start_date "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_adult_and_adolescent_intake " + "    UNION "
		        + "    SELECT client_id, art_start_date "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_pediatric_intake_report " + ") tx ON tx.client_id = p.patient_id "
		        + "LEFT JOIN ( " + "    SELECT client_id, transfer_out, death, client_refused_treatment "
		        + "    FROM ssemr_etl.ssemr_flat_encounter_end_of_follow_up " + ") f ON f.client_id = p.patient_id "
		        + "WHERE tx.art_start_date IS NOT NULL " + "  AND ( " + "      EXISTS ( " + "          SELECT 1 "
		        + "          FROM openmrs.patient_appointment future_appointments "
		        + "          WHERE future_appointments.patient_id = p.patient_id "
		        + "            AND future_appointments.start_date_time > :endDate " + "      ) "
		        + "      OR (p.status = 'Missed' AND DATEDIFF(:endDate, p.start_date_time) <= 28) "
		        + "      OR DATE(p.start_date_time) = DATE(:endDate) " + "  ) "
		        + "  AND DATE(tx.art_start_date) <= DATE(:endDate) " + "  AND (f.death IS NULL OR f.death != 'Yes') "
		        + "  AND (f.transfer_out IS NULL OR f.transfer_out != 'Yes') "
		        + "  AND (f.client_refused_treatment IS NULL OR f.client_refused_treatment != 'Yes') "
		        + "ORDER BY p.patient_id ASC";
		
		Query query = entityManager.createNativeQuery(sql).setParameter("endDate", endDate);
		
		@SuppressWarnings("unchecked")
		List<Integer> patientIds = query.getResultList();
		
		return patientIds;
	}
}
