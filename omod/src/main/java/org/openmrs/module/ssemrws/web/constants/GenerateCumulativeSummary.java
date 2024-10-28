package org.openmrs.module.ssemrws.web.constants;

import java.util.*;
import java.util.stream.Collectors;

public class GenerateCumulativeSummary {
	
	public static Map<String, Map<String, Integer>> generateCumulativeSummary(List<Date> dates) {
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		
		// Step 1: Calculate the monthly summary
		Map<String, Integer> monthlySummary = new HashMap<>();
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			String month = months[calendar.get(Calendar.MONTH)];
			monthlySummary.put(month, monthlySummary.getOrDefault(month, 0) + 1);
		}
		
		// Step 2: Sort the monthly summary
		Map<String, Integer> sortedMonthlySummary = monthlySummary.entrySet().stream()
		        .sorted(Comparator.comparingInt(e -> Arrays.asList(months).indexOf(e.getKey())))
		        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		// Step 3: Make the summary cumulative
		int runningTotal = 0;
		for (String month : sortedMonthlySummary.keySet()) {
			runningTotal += sortedMonthlySummary.get(month);
			sortedMonthlySummary.put(month, runningTotal);
		}
		
		// Step 4: Create the final summary map
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", sortedMonthlySummary);
		
		return summary;
	}
}
