package utils;

import java.util.LinkedHashMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import datatypes.JobList;

public class Storage {
	public static TreeSet<JobList> generalJobList = new TreeSet<>();
	public static TreeSet<JobList> generalBadJobList = new TreeSet<>();
	public static String[] keywords = null;
	public static String[] locations = null;
	public static String[] must_have_kwrds = null;
	public static String[] exclude_kwrds = null;
	public static int searchLimit = 0;
	public static int xpos = 0;
	public static int threads = 1;
	public static boolean twoMonitors = false;
	public static boolean highPriority = false;
	public static boolean playSound = false;
	public static boolean loopSearch = false;
	private final static Logger log = LogManager.getLogger("com.jobFinder");

	public static synchronized int addToStorage(TreeSet<JobList> list, boolean good) {
		// Adding elements one-by-one is necessary because the addAll method
		// does not remove duplicates, which may occur when modifying
		// object's CompanyName field after creation of the list.
		int duplicates = 0;
		for (JobList j : list) {
			if (good) {
				if (generalJobList.contains(j)) {
					duplicates++;
				}
				generalJobList.add(j);
			} else {
				generalBadJobList.add(j);
			}
		}
		if (!good)
			return 0;
		log.info("Added to storage (duplicates excluded): " + (list.size() - duplicates));
		log.info("Storage size " + generalJobList.size());
		return list.size() - duplicates;
	}

	public static void init() {
		Configurator.initialize(null, "log4j2.xml");
		LinkedHashMap<String, String> dic = LoadSettings.getPairs(NamedRanges.general);

		String tmp = dic.get("general_keywords");
		if (!tmp.isBlank()) {
			keywords = tmp.split(",");
		}
		tmp = dic.get("general_locations");
		if (!tmp.isBlank()) {
			locations = tmp.split(",");
		}
		tmp = dic.get("general_required");
		if (!tmp.isBlank()) {
			must_have_kwrds = tmp.split(",");
		}
		tmp = dic.get("general_exclude");
		if (!tmp.isBlank()) {
			exclude_kwrds = tmp.split(",");
		}
		if (dic.get("general_priority").equals("High")) {
			highPriority = true;
		}
		if (dic.get("general_twoMonitors").equals("Yes")) {
			twoMonitors = true;
		}
		if (dic.get("general_loop").toLowerCase().equals("on"))
			loopSearch = true;
		else
			loopSearch = false;
		
		if (dic.get("general_sound").toLowerCase().equals("on"))
			playSound = true;
		else
			playSound = false;
		
		searchLimit = LoadSettings.getDigits(dic.get("general_limit"));
		searchLimit = searchLimit == 0 ? 20 : searchLimit;

		threads = LoadSettings.getDigits(dic.get("general_threads"));
		threads = threads == 0 ? 1 : threads;

		xpos = LoadSettings.getDigits(dic.get("general_xpos"));
	}

}
