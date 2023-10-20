package utils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import datatypes.JobList;
import jobFinder.Paths;

public class Filter {
	// hashes from the file
	public static List<Integer> hashesFromFile = new ArrayList<>();
	public static List<Integer> newHashes = new ArrayList<>();
	private static final Logger log = LogManager.getLogger("com.jobFinder");


	public static void getHashesFromFile() {
		RandomAccessFile file = null;
		try {
			file = new RandomAccessFile(Paths.searchHashFileName, "rw");
			for (int i = 0; i < file.length() / 4; i++) {
				hashesFromFile.add(file.readInt());
			}
			file.close();
		} catch (IOException e) {
			log.info("the hash file wasn't found, will be created");
		}
	}

	// remove cards that were processed previously
	public static void removeExistent() {
		getHashesFromFile();
		log.info("jobList before filtering: " + Storage.generalJobList.size());
		Iterator<JobList> iterator = Storage.generalJobList.iterator();
		while (iterator.hasNext()) {
			JobList j = iterator.next();
			j.hashCode();
			if (hashesFromFile.contains(j.hash)) {
				iterator.remove();
			} else {
				newHashes.add(j.hash);
			}
		}
		log.info("jobList after filtering: " + Storage.generalJobList.size());
	}

	public static void saveNewHashes() {
		RandomAccessFile file = null;
		MakeDir.makeDir(Paths.searchHashFileName);
		try {
			file = new RandomAccessFile(Paths.searchHashFileName, "rw");
			if (file.length() > 0) {
				if (hashesFromFile.size() == 0) {
					file.close();
					throw new RuntimeException("file search_hash not empty and its content wasn't loaded");
				}
			}
			file.seek(file.length());
			for (int i : newHashes) {
				file.writeInt(i);
			}
			file.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log.error("error when writing to " + Paths.searchHashFileName + e.getMessage());
		}
	}

	public static void removeGarbage(Collection<JobList> jobList) {
		Iterator<JobList> iterator = jobList.iterator();
		int before = jobList.size();
		while (iterator.hasNext()) {
			JobList card = iterator.next();
			boolean save=true;
			if (Storage.must_have_kwrds != null) {
				save =
						// must be in must_have and not be in exclude list
						presentInArray(card.jobTitle, Storage.must_have_kwrds)
								& !presentInArray(card.jobTitle, Storage.exclude_kwrds);
			} else {
				// must not be in exclude list
				save = !presentInArray(card.jobTitle, Storage.exclude_kwrds);
			}

			if (!save) {
				log.info("Garbage: " + card.jobTitle);
				iterator.remove();
			}
		}
		log.info("Garbage cards removed: " + (before - jobList.size()));
	}

	private static boolean presentInArray(String jobTitle, String[] list) {
		if (list != null) {
			for (String keyword : list) {
				if (jobTitle.trim().toLowerCase().contains(keyword.trim().toLowerCase())) {
					return true;
				}
			}
		}
		return false;
	}

}
