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
	
	public HashSet<Patient> getOnAppoinment(Date startDate, Date endDate) {
		// Execute the query
		List<Integer> patientIds = (List<Integer>) executePatientQuery(startDate, endDate, false);
		return fetchPatientsByIds(patientIds);
	}
	
	private Object executePatientQuery(Date startDate, Date endDate, boolean isCountQuery) {
		String baseQuery = getQueryString(isCountQuery);
		
		try {
			// Create and configure the query
			Query query = entityManager.createNativeQuery(baseQuery).setParameter("startDate", startDate)
			        .setParameter("endDate", endDate);
			
			if (isCountQuery) {
				BigInteger totalCount = (BigInteger) query.getSingleResult();
				return totalCount.intValue();
			} else {
				return query.getResultList();
			}
		}
		catch (Exception e) {
			System.err.println("Error executing patient query: " + e.getMessage());
			throw new RuntimeException("Failed to execute patient query", e);
		}
	}
	
	private static String getQueryString(boolean isCountQuery) {
		String selectClause = isCountQuery ? "COUNT(DISTINCT fp.patient_id)" : "DISTINCT fp.patient_id";
		
		// Base query to fetch patients based on appointment dates
		String baseQuery = "SELECT " + selectClause + " " + "FROM openmrs.patient_appointment fp "
		        + "JOIN openmrs.person p ON fp.patient_id = p.person_id " + "WHERE fp.start_date_time >= :startDate "
		        + "  AND fp.start_date_time < DATE_ADD(:endDate, INTERVAL 1 DAY)";
		
		return baseQuery;
	}
}
