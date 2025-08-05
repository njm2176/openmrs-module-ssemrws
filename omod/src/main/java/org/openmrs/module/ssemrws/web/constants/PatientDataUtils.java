package org.openmrs.module.ssemrws.web.constants;

import org.openmrs.Patient;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.openmrs.module.ssemrws.constants.GetDateObservations.getLatestDateFromObs;

@Component
public class PatientDataUtils {
	
	private static final SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
	
	/**
	 * Get the latest date from two different observation concepts and format it. This removes code
	 * duplication from the public methods.
	 * 
	 * @param patient The patient object.
	 * @param conceptUuid1 The UUID for the first concept to check.
	 * @param conceptUuid2 The UUID for the second concept to check.
	 * @return A formatted string of the latest date found.
	 */
	public static String getLatestDateForConcepts(Patient patient, String conceptUuid1, String conceptUuid2) {
		Date date1 = getLatestDateFromObs(patient, conceptUuid1);
		Date date2 = getLatestDateFromObs(patient, conceptUuid2);
		Date latestDate = getLatestDate(date1, date2);
		return formatDate(latestDate);
	}
	
	private static Date getLatestDate(Date date1, Date date2) {
		if (date1 == null) {
			return date2;
		}
		if (date2 == null) {
			return date1;
		}
		return date1.after(date2) ? date1 : date2;
	}
	
	private static String formatDate(Date date) {
		if (date == null) {
			return "";
		}
		
		return formatter.format(date);
	}
}
