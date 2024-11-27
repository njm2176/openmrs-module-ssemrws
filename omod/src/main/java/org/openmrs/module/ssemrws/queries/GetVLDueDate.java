package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class GetVLDueDate {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	// Query to fetch the VL due date for a specific patient
	public String getVLDueDate(Patient patient) {
		String query = "select t.client_id, DATE_FORMAT(MAX(t.due_date), '%d-%m-%Y') AS max_due_date "
		        + "from (SELECT fp.client_id, mp.age, fp.vl_results, fp.viral_load_value, fp.date_vl_sample_collected, fp.edd, fh.art_start_date, "
		        + "fp.client_pregnant, fp.encounter_datetime, vlr.value, "
		        + " CASE WHEN mp.age <= 18 THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
		        + " WHEN fp.edd IS NOT NULL AND fp.edd > CURDATE() AND MAX(DATE(fh.were_arvs_received)) = CURDATE() THEN fp.encounter_datetime "
		        + " WHEN fp.edd IS NOT NULL AND fp.edd > CURDATE() AND MAX(DATE(fh.were_arvs_received)) > CURDATE() THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + " WHEN mp.age > 18 AND fp.viral_load_value >= 1000 THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + " WHEN mp.age > 18 AND fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)' THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 12 MONTH) "
		        + " ELSE NULL END as due_date " + "FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr ON vlr.client_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.mamba_dim_person mp ON mp.person_id = fp.client_id "
		        + "LEFT JOIN ssemr_etl.ssemr_flat_encounter_personal_family_tx_history fh on fh.client_id = fp.client_id "
		        + "WHERE fp.client_id = :patientId AND DATE(fp.encounter_datetime) <= CURRENT_DATE "
		        + "GROUP BY fp.client_id, mp.age, fp.vl_results, fp.viral_load_value, fp.edd, fh.art_start_date, fp.client_pregnant, vlr.value, fp.encounter_datetime, fp.date_vl_sample_collected) t "
		        + "group by client_id";
		
		// Execute the query with the patient ID as a parameter
		List<Object[]> results = entityManager.createNativeQuery(query).setParameter("patientId", patient.getPatientId())
		        .getResultList();
		
		// Return the next VL due date if found, otherwise return null
		if (!results.isEmpty() && results.get(0) != null) {
			Object[] resultRow = results.get(0);
			return (String) resultRow[1];
		}
		
		return null;
	}
}
