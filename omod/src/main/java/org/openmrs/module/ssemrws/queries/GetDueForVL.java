package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.VlEligibilityResult;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetDueForVL {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	private Object executeDueForVlQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String selectClause = isCountQuery ? "count(distinct fp.client_id)" : "distinct fp.client_id";
		String baseQuery = "select " + selectClause + " from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id " + "where ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000), not breastfeeding,
				// next due VL in 6 months or 12 months
		        + "   (mp.age > 18 and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 "
		        + "    and fp.viral_load_value < 1000 and (TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6 "
		        + "    or TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 12)) "
				// Criteria 2: Child/Adolescent up to 18 years old, next VL in 6 months, join
				// criteria 1 at age 19
		        + "or (mp.age <= 18 and TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6) "
				// Criteria 3: Pregnant women newly enrolled on ART, due for VL every 3 months
				// while in PMTCT
		        + "or (fp.client_pregnant = 'Yes' and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) < 6 "
		        + "    and TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, :endDate) >= 3) "
				// Criteria 4: Pregnant woman already on ART, eligible immediately after
				// discovering pregnancy
		        + "or (fp.client_pregnant = 'Yes' and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6) "
				// Criteria 5: Post EAC 3, eligible for VL in 1 month
		        + "or (hvl.eac_session = 'Third EAC Session' and TIMESTAMPDIFF(MONTH, hvl.adherence_date, :endDate) >= 1) "
		        + ") " + "and fp.encounter_datetime between :startDate and :endDate";
				
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalDueForVl = (BigInteger) query.getSingleResult();
				return totalDueForVl.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (NoResultException e) {
			System.err.println("No data found for the query: " + e.getMessage());
			return new ArrayList<>();
		}
		catch (Exception e) {
			System.err.println("Error executing Due for VL query: " + e.getMessage());
			throw new RuntimeException("Failed to execute Due for VL query", e);
		}
	}
	
	// Method to fetch the list of TxCurr patients
	public HashSet<Patient> getDueForVl(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeDueForVlQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	public VlEligibilityResult isPatientDueForVl(String patientUuid) {
		String baseQuery = "select count(distinct fp.client_id), fp.date_vl_sample_collected from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id "
		        + "where mp.uuid = :patientUuid and ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000)
		        + "   (mp.age > 18 and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) >= 6 "
		        + "    and fp.viral_load_value < 1000 and (TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 6 "
		        + "    or TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 12)) "
				// Criteria 2: Child/Adolescent up to 18 years old
		        + "or (mp.age <= 18 and TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, NOW()) >= 6) "
				// Criteria 3: Pregnant women newly enrolled on ART
		        + "or (fp.client_pregnant = 'Yes' and pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) < 6 "
		        + "    and TIMESTAMPDIFF(MONTH, vlr.date_of_sample_collection, NOW()) >= 3) "
				// Criteria 4: Pregnant woman already on ART
		        + "or (fp.client_pregnant = 'Yes' and TIMESTAMPDIFF(MONTH, pfh.art_start_date, NOW()) >= 6) "
				// Criteria 5: Post EAC 3
		        + "or (hvl.eac_session = 'Third EAC Session' and TIMESTAMPDIFF(MONTH, hvl.adherence_date, NOW()) >= 1) "
		        + ")";
				
		String dueDateQuery = "select t.client_id, DATE_FORMAT(MAX(t.due_date), '%Y-%m-%d') AS max_due_date from (SELECT fp.client_id, mp.age, "
		        + "fp.vl_results, fp.date_vl_sample_collected, fp.edd, fh.art_start_date, "
		        + "vlr.patient_pregnant, fp.encounter_datetime, vlr.value, "
		        + " CASE WHEN mp.age <= 19 THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 6 MONTH) "
		        + " WHEN fp.edd IS NOT NULL AND fp.edd > CURDATE() AND MAX(DATE(fh.were_arvs_received)) = CURDATE() THEN fp.encounter_datetime "
		        + " WHEN fp.edd IS NOT NULL AND fp.edd > CURDATE() AND MAX(DATE(fh.were_arvs_received)) > CURDATE() THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + "  WHEN mp.age > 19 AND fp.vl_results >= 200 THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 3 MONTH) "
		        + " WHEN mp.age > 19 AND fp.vl_results < 200 THEN DATE_ADD(fp.date_vl_sample_collected, INTERVAL 12 MONTH) "
		        + "  ELSE NULL END as due_date FROM ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + " LEFT JOIN ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + " LEFT JOIN ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr ON vlr.client_id = fp.client_id "
		        + " LEFT JOIN ssemr_etl.mamba_dim_person mp ON mp.person_id = fp.client_id "
		        + " LEFT JOIN ssemr_etl.ssemr_flat_encounter_personal_family_tx_history fh on fh.client_id = fp.client_id "
		        + "  WHERE DATE(fp.encounter_datetime) <= CURDATE() GROUP BY fp.client_id,mp.age,fp.vl_results,fp.edd,fh.art_start_date, "
		        + "  vlr.patient_pregnant,vlr.value,fp.encounter_datetime,fp.date_vl_sample_collected) t WHERE t.client_id = :patientUuid group by client_id";
		
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("patientUuid", patientUuid);
			Query dueDateQueryObj = entityManager.createNativeQuery(dueDateQuery).setParameter("patientUuid", patientUuid);
			
			// Fetch the result and date
			Object[] result = (Object[]) query.getSingleResult();
			BigInteger resultCount = (BigInteger) result[0];
			Date lastVlSampleDate = (Date) result[1];
			
			// Fetch the due date result
			Object[] dueDateResult = (Object[]) dueDateQueryObj.getSingleResult();
			String maxDueDateStr = (String) dueDateResult[1];
			Date vlDueDate = null;
			if (maxDueDateStr != null) {
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				vlDueDate = formatter.parse(maxDueDateStr);
			}
			
			// Check if patient is eligible
			boolean isEligible = resultCount.intValue() > 0;
			
			return new VlEligibilityResult(isEligible, vlDueDate);
		}
		catch (NoResultException e) {
			System.err.println("No data found for the query: " + e.getMessage());
			return new VlEligibilityResult(false, null);
		}
		catch (Exception e) {
			System.err.println("Error executing VL eligibility query: " + e.getMessage());
			throw new RuntimeException("Failed to check VL eligibility", e);
		}
	}
}
