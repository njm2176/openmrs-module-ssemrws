package org.openmrs.module.ssemrws.queries;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import static org.openmrs.module.ssemrws.web.constants.FetchPatientsByIdentifier.fetchPatientsByIds;

@Component
public class GetOnAppointment {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	// Method to fetch the list of IIT patients
	public HashSet<Patient> getOnAppoinment(Date startDate, Date endDate) {
		List<Integer> iitIds = (List<Integer>) executePatientQuery(startDate, endDate, false, "Scheduled", false, false);
		return fetchPatientsByIds(iitIds);
	}
	
	private Object executePatientQuery(Date startDate, Date endDate, boolean isCountQuery, String status,
	        boolean useCutoffDate, boolean isIit) {
		String baseQuery = getQueryString(isCountQuery, status, useCutoffDate, isIit);
		
		try {
			// Create and configure the query
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			// Set the status parameter if required
			if (status != null) {
				query.setParameter("status", status);
			}
			
			// Set the cutoff date if required
			if (useCutoffDate) {
				// Calculate the cutoff date (28 days ago from today)
				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.DAY_OF_YEAR, -28);
				Date cutoffDate = calendar.getTime();
				
				if (isIit) {
					query.setParameter("cutoffDate", cutoffDate);
				} else {
					query.setParameter("cutoffDate", cutoffDate);
				}
			}
			
			// Execute the query based on `isCountQuery`
			if (isCountQuery) {
				BigInteger totalCount = (BigInteger) query.getSingleResult();
				return totalCount.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			// Log the error and rethrow a runtime exception
			System.err.println("Error executing patient query: " + e.getMessage());
			throw new RuntimeException("Failed to execute patient query", e);
		}
	}
	
	private static String getQueryString(boolean isCountQuery, String status, boolean useCutoffDate, boolean isIit) {
		String selectClause = isCountQuery ? "count(distinct fp.patient_id)" : "distinct fp.patient_id";
		
		// Start constructing the query
		String baseQuery = "select " + selectClause + " from openmrs.patient_appointment fp "
		        + "join openmrs.person p on fp.patient_id = p.person_id "
		        + (status != null ? "where fp.status = :status " : "where 1=1 ");
		
		if (useCutoffDate) {
			if (isIit) {
				// IIT: missed appointment more than 28 days ago
				baseQuery += "and fp.start_date_time < :cutoffDate ";
			} else {
				// Missed appointment within the past 28 days
				baseQuery += "and fp.start_date_time >= :cutoffDate ";
			}
		}
		
		baseQuery += "and fp.start_date_time between :startDate and :endDate";
		return baseQuery;
	}
}
