package org.openmrs.module.ssemrws.web.constants;

import java.util.*;
import java.util.stream.Collectors;

public class GenerateCumulativeSummary {
	
	public static Map<String, Map<String, Integer>> generateCumulativeSummary(List<Date> dates, Date startDate, Date endDate,
	        int totalPatients) {
		String[] months = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
		        "Dec" };
		
		// Step 1: Organize patients into their correct month/year
		Map<Integer, Map<String, Integer>> yearMonthSummary = new TreeMap<>();
		
		for (Date date : dates) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(date);
			
			int year = calendar.get(Calendar.YEAR);
			String month = months[calendar.get(Calendar.MONTH)];
			
			yearMonthSummary.putIfAbsent(year, new HashMap<>());
			Map<String, Integer> monthlySummary = yearMonthSummary.get(year);
			monthlySummary.put(month, monthlySummary.getOrDefault(month, 0) + 1);
		}
		
		// Step 2: Create a cumulative summary for all dates
		Map<String, Integer> cumulativeSummary = new LinkedHashMap<>();
		int runningTotal = 0;
		for (Map.Entry<Integer, Map<String, Integer>> yearEntry : yearMonthSummary.entrySet()) {
			for (String month : months) {
				if (yearEntry.getValue().containsKey(month)) {
					runningTotal += yearEntry.getValue().get(month);
				}
				cumulativeSummary.put(month, runningTotal);
			}
		}
		
		// Step 3: Ensure total patients count is correct in the last month
		Calendar endCal = Calendar.getInstance();
		endCal.setTime(endDate);
		String lastMonth = months[endCal.get(Calendar.MONTH)];
		
		// Force last month count to be exactly totalPatients
		cumulativeSummary.put(lastMonth, totalPatients);
		
		// Step 4: Filter the summary for the selected date range
		Calendar startCal = Calendar.getInstance();
		startCal.setTime(startDate);
		
		int startMonth = startCal.get(Calendar.MONTH);
		int endMonth = endCal.get(Calendar.MONTH);
		
		Map<String, Integer> filteredCumulativeSummary = new LinkedHashMap<>();
		for (int i = startMonth; i <= endMonth; i++) {
			String month = months[i];
			if (cumulativeSummary.containsKey(month)) {
				filteredCumulativeSummary.put(month, cumulativeSummary.get(month));
			}
		}
		
		// Step 5: Ensure total sum matches totalPatients
		if (filteredCumulativeSummary.get(lastMonth) != totalPatients) {
			System.err.println("Warning: Adjusting final month count to match total patients.");
		}
		
		// Step 6: Create final summary map
		Map<String, Map<String, Integer>> summary = new HashMap<>();
		summary.put("groupYear", filteredCumulativeSummary);
		
		return summary;
	}
	
	private static Date getEndOfMonth(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
		return cal.getTime();
	}
}
