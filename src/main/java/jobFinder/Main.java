package jobFinder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import utils.*;

public class Main {
	public final static Logger log = LogManager.getLogger("com.jobFinder");

	public static void main(String[] args) throws InterruptedException {

		// setting args from cli
		setArgs(args);
		do { //this loop will be active while Storage.loopSearch is true
			
			// loading settings from excel file
			Storage.init();

			// List of threads to run
			List<MyThread> threads = new ArrayList<>();

			// Each method of this class will be run in a separate thread
			RunCollection myMethods = new RunCollection();

			// getting all methods of the RunCollection
			Method[] methods = myMethods.getClass().getDeclaredMethods();

			// sorting the array
			Arrays.sort(methods, Comparator.comparing(Method::getName));

			// convert all methods in the class
			// to list of threads
			for (Method method : methods) {
				// adding new anonymous class with two methods to the threads list
				threads.add(new MyThread() {
					@Override
					public void run() {
						try {
							Thread.currentThread().setName(method.getName());
							method.invoke(myMethods, xPos);
						} catch (Exception e) {
							log.error(e.toString());
						}
					}

				});
			}
			RunLimitedListOfThreads.runThreadList(threads, Storage.threads);

			// removes all jobs that were assessed before
			Filter.removeExistent();

			// assess jobs
			OpenAI.assessJobList();

			WriteExcelReport.writeReport();

			// save new hashes for future, only if list was assessed
			if (OpenAI.initialized) {
				Filter.saveNewHashes();
			}

		} while (Storage.loopSearch);

	}

	private static void setArgs(String[] args) {

		for (int i = 0; i < args.length; i++) {
			if (args[0].toLowerCase().equals("help")) {
				System.out.println("Example: mySearch.xlsx myReport.xlsx");
				System.exit(0);
			}
			String regexFn = "(?:\\./[\\w/]+/)?([\\w.-]+)";

			switch (i) {
			case 0:
				if (Pattern.compile(regexFn).matcher(args[0]).matches()) {
					Paths.settingsFileName = args[0];
				}
				log.info("\nsettings: " + Paths.settingsFileName);
				break;
			case 1:
				if (Pattern.compile(regexFn).matcher(args[1]).matches()) {
					Paths.reportFileName = args[1];
					//path to hashfile
					String[] parts = args[1].split("/");
					String fileNameWithExtension = parts[parts.length - 1];
					String[] fileNameParts = fileNameWithExtension.split("\\.");
					Paths.searchHashFileName = "./hashes/" + fileNameParts[0] + ".hash";
					//path to resume folder
					Matcher matcher = Pattern.compile("^(.+/).*?$").matcher(args[1]);
					if (matcher.find()) {
						Paths.resumePath = matcher.group(1) + "Resumes/";
					}
				}
				log.info("\nreport: " + Paths.reportFileName);
				break;
			}
		}
	}

}
