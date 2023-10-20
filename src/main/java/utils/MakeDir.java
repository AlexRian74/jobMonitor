package utils;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MakeDir {
	public final static Logger log = LogManager.getLogger("com.jobFinder");
	
	public static void makeDir(String fileName) {
		File file = new File(fileName);
		File parentDirectory = file.getParentFile();
		
		if (parentDirectory == null)
			return;
		
		// Create the parent directory along with any necessary subdirectories.
		if (!parentDirectory.exists()) {
			if (parentDirectory.mkdirs()) {
				log.info("Parent directories created successfully.");
			} else {
				log.info("Failed to create parent directory.");
				return;
			}
		}
	}
}
