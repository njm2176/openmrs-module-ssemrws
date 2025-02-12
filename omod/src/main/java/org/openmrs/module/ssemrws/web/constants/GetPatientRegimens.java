package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.constants.SharedConstants.*;

@Component
public class GetPatientRegimens {
	
	public Object getFilteredPatientsOnRegimenTreatment(String qStartDate, String qEndDate,
	        List<String> adultRegimenConceptUuids, List<String> childRegimenConceptUuids, String activeRegimenConceptUuid,
	        List<GetTxNew.PatientEnrollmentData> txCurrPatients, boolean isAdultCategory) throws ParseException {
		
		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
		Date[] dates = getStartAndEndDate(qStartDate, qEndDate, dateTimeFormatter);
		
		// Get the IDs of txCurr patients
		Set<Integer> txCurrPatientIds = txCurrPatients.stream().map(data -> data.getPatient().getPatientId())
		        .collect(Collectors.toSet());
		
		// Fetch both adult and child regimen observations
		List<Obs> adultRegimenObs = getRegimenTreatmentObservations(dates, adultRegimenConceptUuids,
		    activeRegimenConceptUuid);
		List<Obs> childRegimenObs = getRegimenTreatmentObservations(dates, childRegimenConceptUuids,
		    activeRegimenConceptUuid);
		
		// Combine observations and determine the latest observation for each patient
		Map<Integer, Obs> latestObsByPatient = getLatestObservationsAcrossCategories(txCurrPatientIds, adultRegimenObs,
		    childRegimenObs);
		
		// Filter patients based on category (adult or child)
		Map<Integer, Obs> filteredObsByPatient = latestObsByPatient.entrySet().stream().filter(
		    entry -> isAdultCategory == adultRegimenConceptUuids.contains(entry.getValue().getValueCoded().getUuid()))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		
		// Count regimens
		Map<String, Integer> regimenCounts = countRegimens(filteredObsByPatient);
		
		return prepareResults(regimenCounts);
	}
	
	private List<Obs> getRegimenTreatmentObservations(Date[] dates, List<String> regimenConceptUuids,
	        String activeRegimenConceptUuid) {
		List<Concept> regimenConcepts = getConceptsByUuids(regimenConceptUuids);
		return Context.getObsService().getObservations(null, null,
		    Collections.singletonList(Context.getConceptService().getConceptByUuid(activeRegimenConceptUuid)),
		    regimenConcepts, null, null, null, 0, null, null, dates[1], false);
	}
	
	private Map<Integer, Obs> getLatestObservationsAcrossCategories(Set<Integer> txCurrPatientIds, List<Obs> adultRegimenObs,
	        List<Obs> childRegimenObs) {
		Map<Integer, Obs> latestObsByPatient = new HashMap<>();
		
		// Combine adult and child observations into one list
		List<Obs> allObservations = new ArrayList<>();
		allObservations.addAll(adultRegimenObs);
		allObservations.addAll(childRegimenObs);
		
		// Determine the latest observation for each patient
		for (Obs obs : allObservations) {
			Integer patientId = obs.getPerson().getPersonId();
			if (txCurrPatientIds.contains(patientId)) {
				if (!latestObsByPatient.containsKey(patientId)
				        || obs.getObsDatetime().after(latestObsByPatient.get(patientId).getObsDatetime())) {
					latestObsByPatient.put(patientId, obs);
				}
			}
		}
		
		return latestObsByPatient;
	}
	
	private Map<String, Integer> countRegimens(Map<Integer, Obs> latestObsByPatient) {
		Map<String, Integer> regimenCounts = new HashMap<>();
		for (Obs obs : latestObsByPatient.values()) {
			if (obs != null) {
				Concept regimenConcept = obs.getValueCoded();
				if (regimenConcept != null && regimenConcept.getName() != null) {
					String conceptName = regimenConcept.getName().getName();
					regimenCounts.put(conceptName, regimenCounts.getOrDefault(conceptName, 0) + 1);
				}
			}
		}
		return regimenCounts;
	}
	
	private Map<String, Object> prepareResults(Map<String, Integer> regimenCounts) {
		Map<String, Object> results = new HashMap<>();
		List<Map<String, Object>> regimenList = new ArrayList<>();
		
		for (Map.Entry<String, Integer> entry : regimenCounts.entrySet()) {
			Map<String, Object> regimenEntry = new HashMap<>();
			regimenEntry.put("text", entry.getKey());
			regimenEntry.put("total", entry.getValue());
			regimenList.add(regimenEntry);
		}
		
		results.put("results", regimenList);
		return results;
	}
	
	public static Date[] getStartAndEndDate(String qStartDate, String qEndDate, SimpleDateFormat dateTimeFormatter)
	        throws ParseException {
		Date endDate = (qEndDate != null) ? dateTimeFormatter.parse(qEndDate) : new Date();
		
		// Extend endDate to 23:59:59
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(endDate);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);
		endDate = calendar.getTime();
		
		// Set startDate correctly
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date startDate = (qStartDate != null) ? dateTimeFormatter.parse(qStartDate) : calendar.getTime();
		
		return new Date[] { startDate, endDate };
	}
}
