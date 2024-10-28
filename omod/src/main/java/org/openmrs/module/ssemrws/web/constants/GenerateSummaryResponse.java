package org.openmrs.module.ssemrws.web.constants;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Patient;
import org.openmrs.module.ssemrws.constants.SharedConstants;
import org.openmrs.module.ssemrws.web.controller.SSEMRWebServicesController;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.openmrs.module.ssemrws.web.constants.PaginatedPages.*;

@Component
public class GenerateSummaryResponse {
	
	private final PaginatedPages paginatedPages;
	
	public GenerateSummaryResponse(PaginatedPages paginatedPages) {
		this.paginatedPages = paginatedPages;
	}
	
	public Object generateSummaryResponse(List<Patient> patientList, int page, int size, String totalKey, int totalCount,
	        Date startDate, Date endDate, SSEMRWebServicesController.filterCategory filterCategory,
	        Function<List<Date>, Map<String, Map<String, Integer>>> summaryGenerator) {
		// Step 1: Calculate the summary based on the full patient list
		List<Date> patientDates = patientList.stream().map(SharedConstants::getInitiationDate).filter(Objects::nonNull)
		        .collect(Collectors.toList());
		
		Map<String, Map<String, Integer>> summary = summaryGenerator.apply(patientDates);
		
		// Step 2: Paginate the patient list
		Object paginatedResponse = paginatedPages.fetchAndPaginatePatients(patientList, page, size, totalKey, totalCount,
		    startDate, endDate, filterCategory);
		
		// Convert to ObjectNode if it's a String (which could be a JSON string)
		ObjectNode responseObj;
		if (paginatedResponse instanceof String) {
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				responseObj = (ObjectNode) objectMapper.readTree((String) paginatedResponse);
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to parse paginated response as JSON", e);
			}
		} else {
			responseObj = (ObjectNode) paginatedResponse;
		}
		
		// Step 3: Add the summary to the paginated response
		ObjectNode groupingObj = JsonNodeFactory.instance.objectNode();
		ObjectNode groupYear = JsonNodeFactory.instance.objectNode();
		
		// Populate the summary into the response
		summary.get("groupYear").forEach(groupYear::put);
		
		groupingObj.put("groupYear", groupYear);
		
		responseObj.put("summary", groupingObj);
		
		return responseObj.toString();
	}
}
