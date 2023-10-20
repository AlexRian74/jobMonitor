package utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GetDateString {
	public static String getDate() {
		String pattern = "MMMM-dd-yyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		return simpleDateFormat.format(new Date());
	}
	public static String getTime() {
		String pattern = "HH:mm";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		return simpleDateFormat.format(new Date());
	}
}
