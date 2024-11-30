package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier;
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
	
	private Object executeDueForVlQuery(Date startDate, Date endDate) {
		String selectClause = "distinct fp.client_id";
		String baseQuery = "select " + selectClause + " from ssemr_etl.ssemr_flat_encounter_hiv_care_follow_up fp "
		        + "join ssemr_etl.mamba_dim_person mp on fp.client_id = mp.person_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_hiv_care_enrolment en ON en.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_personal_family_tx_history pfh on pfh.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_vl_laboratory_request vlr on vlr.client_id = fp.client_id "
		        + "left join ssemr_etl.ssemr_flat_encounter_high_viral_load hvl on hvl.client_id = fp.client_id " + "where ("
				// Criteria 1: Adults, ART > 6 months, VL suppressed (<1000), not breastfeeding,
				// next due VL in 6 months or 12 months
		        + " (mp.age > 18 AND pfh.art_start_date is not null and TIMESTAMPDIFF(MONTH, pfh.art_start_date, :endDate) >= 6 "
		        + " AND fp.client_breastfeeding = 'No'"
		        + " AND fp.viral_load_value < 1000 OR fp.vl_results = 'Below Detectable (BDL)'"
		        + " AND (TIMESTAMPDIFF(MONTH, fp.date_vl_sample_collected, :endDate) >= 6 "
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
			return query.getResultList();
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
		List<Integer> patientIds = (List<Integer>) executeDueForVlQuery(startDate, endDate);
		return fetchPatientsByIds(patientIds);
	}
}
