package pages;

import java.util.List;
import java.util.TreeSet;

import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;

import datatypes.JobList;
import datatypes.Settings;
import datatypes.WaitType;
import utils.Filter;
import utils.LoadSettings;
import utils.Storage;

public class Google extends BaseDriver {
	private final String JOB_TITLE = "//li[@class='iFjolb gws-plugins-horizon-jobs__li-ed']/div/div[2]/div[2]/div/div/div[2]/div[2]";
	private final String FILTER_REQ = "//*[@data-facet='requirements']//span[contains(text(),'value')]";
	private final String JOB_SECTION = "//div[@id='tl_ditc']/div";
	private final String SHOW_MORE = "//div[@id='tl_ditc']/div//div[contains(text(),'value')]";
	private final String JOB_DESCRIPTION = "//div[@id='tl_ditc']/div //div[@class='config-text-expandable']";
	private final String NO_JOBS_FOUND_MSG = "//div[@class='h1N1Ee']";
	private final String COMPANY_NAME = "//div[@id='tl_ditc']/div [@id=\"gws-plugins-horizon-jobs__job_details_page\"]/div/div[1]/div/div[2]/div[2]/div[1]";

	private boolean run = false;
	private String[] searchArea = { "NY", "NJ" };
	private String[] keywords = { "Software QA", "Software tester" };
	private int google_limit = 100;
	private String jobType;
	private String jobAge;
	private TreeSet<JobList> jobList = new TreeSet<>();
	private String searchKeyword;
	private final String startUrl = "https://www.google.com/search?q=keyword&ibp=htl;jobs";

	public Google(int xPos) {
		loadSettings();
		if (run == false) {
			log.info("execution of google is disabled");
			return;
		}
		int cardsAdded = 0;
		super.setUp(startUrl, xPos);
		first: for (String location : searchArea) {
			for (String keyword : keywords) {
				searchKeyword = keyword;
				try {
					driver.get(startUrl.replace("keyword", keyword + " in " + location));
					if (!setFilter())
						continue;

					scrollPage(JOB_TITLE, google_limit);

					if (getElement(NO_JOBS_FOUND_MSG, WaitType.PRESENCE).isDisplayed()) {
						System.out.println("No jobs");
						continue;
					}

					getCards();

					Filter.removeGarbage(jobList);

					cardsAdded = cardsAdded + Storage.addToStorage(jobList, true);

					jobList.clear();

				} catch (NoSuchWindowException e) {
					log.error("The browser was closed");
					break first; // exiting the loop
				} catch (Exception e) {
					log.error(e.toString());
				}

			}
		}
		log.info("Added cards from Google: " + cardsAdded);
		super.close();
	}

	private void getCards() {
		List<WebElement> jobTitles=null;
		for (int i = 3; i > 0; i--) {
			jobTitles = getElements(JOB_TITLE);
			break;
		}
		if (jobTitles == null)
			return;

		int counter = 0;
		for (WebElement card : jobTitles) {
			if (counter == google_limit)
				break;
			else
				counter++;

			String jobTitle = "";
			String href = "";
			String companyName = "";
			String jobDescription = "";
			// re-try mechanism
			for (int i = 3; i > 0; i--) {
				try {
					js.executeScript("arguments[0].scrollIntoView(true);", card);
					card.click();

					jobTitle = card.getText();
					jobTitle = jobTitle.strip();

					WebElement cName = getElement(COMPANY_NAME, WaitType.PRESENCE);
					companyName = cName.getText();

					WebElement jobSection = getElement(JOB_SECTION, WaitType.PRESENCE);
					href = jobSection.getAttribute("data-share-url");

					String showMoreXPath = SHOW_MORE.replace("value", "Show full");
					WebElement tmp = getElement(showMoreXPath, WaitType.PRESENCE);
					if (tmp != null) {// this btn isn't always present
						if (tmp.isDisplayed())
							getElement(showMoreXPath, WaitType.CLICKABLE).click();
					}

					WebElement jobD = getElement(JOB_DESCRIPTION, WaitType.PRESENCE);
					if (jobD != null)
						jobDescription = jobD.getText();

					break; // exit from re-try
				} catch (Exception e) {
					if (e instanceof NoSuchWindowException) {
						throw (NoSuchWindowException) e;
					}
					log.error(e);
				}
			}
			jobList.add(new JobList(jobTitle, companyName, jobDescription, href, searchKeyword));
			log.info("Added card #" + counter);
		}
	}

	private boolean setFilter() {
		log.info("Setting filters");
		String menu = CONTAINS_TEXT.replace("value", "Date posted");
		String option = CONTAINS_TEXT.replace("value", jobAge) + "/../..";
		if (!setFilterOption(menu, option)) {
			log.info("can't set Date posted filter");
			return false;
		}

		menu = FILTER_REQ.replace("value", "Requirements");
		option = FILTER_REQ.replace("value", jobType) + "/../..";
		if (!setFilterOption(menu, option)) {
			log.info("can't set Experience level filter");
			return false;
		}

		log.info("Filters were set");
		return true;
	}

	private boolean setFilterOption(String menuXPath, String optionXPath) {
		WebElement datePostedBtn = getElement(menuXPath, WaitType.CLICKABLE);
		if (datePostedBtn == null)
			return false;
		datePostedBtn.click();

		WebElement option = getElement(optionXPath, WaitType.CLICKABLE);
		if (option == null)
			return false;
		option.click();

		return true;
	}

	public void loadSettings() {
		Settings settings = LoadSettings.getSettings("Google");
		jobType = settings.jobType.contains("Not") ? "All" : settings.jobType;
		jobAge = settings.timePosted.contains("Not") ? "All" : settings.timePosted;
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		searchArea = settings.locations != null ? settings.locations : Storage.locations;
		google_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		run = settings.run;
		if (keywords == null || searchArea == null) {
			log.info("Keywords or locations wasn't set");
			run = false;
		}
	}

}
