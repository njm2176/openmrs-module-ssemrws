package org.openmrs.module.ssemrws.web.constants;

import org.jfree.data.time.DateRange;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateRangeCalculation {
	
	static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd");
	
	private DateRange parseDateRange(String startDate, String endDate) throws ParseException {
		Date start = dateTimeFormatter.parse(startDate);
		Date end = dateTimeFormatter.parse(endDate);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(end);
		calendar.add(Calendar.DAY_OF_MONTH, 1);
		return new DateRange(start, calendar.getTime());
	}
}
