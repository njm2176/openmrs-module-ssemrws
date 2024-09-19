package org.openmrs.module.ssemrws.service;

import org.openmrs.Patient;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.Date;
import java.util.List;

@Service
public class SsemrPatientService {
	
	@PersistenceContext
	private EntityManager entityManager;
	
	@Transactional(readOnly = true)
	public List<Patient> getFilteredPatients(Date startDate, Date endDate,
	        SSEMRWebServicesController.filterCategory filterCategory, int page, int size) {
		StringBuilder queryBuilder = new StringBuilder("SELECT p FROM Patient p WHERE p.voided = false");
		
		// Add date range filter
		if (startDate != null && endDate != null) {
			queryBuilder.append(" AND p.dateCreated BETWEEN :startDate AND :endDate");
		}
		
		if (filterCategory != null) {
			queryBuilder.append(" AND p.category = :filterCategory");
		}
		
		TypedQuery<Patient> query = entityManager.createQuery(queryBuilder.toString(), Patient.class);
		
		if (startDate != null && endDate != null) {
			query.setParameter("startDate", startDate);
			query.setParameter("endDate", endDate);
		}
		
		if (filterCategory != null) {
			query.setParameter("filterCategory", filterCategory);
		}
		
		query.setFirstResult(page * size);
		query.setMaxResults(size);
		
		return query.getResultList();
	}
}
