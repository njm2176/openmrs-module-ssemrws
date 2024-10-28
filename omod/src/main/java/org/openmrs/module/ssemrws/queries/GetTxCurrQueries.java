package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetTxCurrQueries {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	public HashSet<Patient> getTxCurr(Date startDate, Date endDate) {
		List<Integer> patientIds = (List<Integer>) executeTxCurrQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	private Object executeTxCurrQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id " + "where (fp.start_date_time >= :now "
		        + "or (fp.start_date_time between :startDate and :endDate "
		        + "and date(fp.start_date_time) >= current_date() - interval 28 day))";
		
		try {
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("now", new Date())
			        .setParameter("startDate", startDate).setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalTxCurr = (BigInteger) query.getSingleResult();
				return totalTxCurr.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			System.err.println("Error executing TxCurr query: " + e.getMessage());
			throw new RuntimeException("Failed to execute TxCurr query", e);
		}
	}
}
