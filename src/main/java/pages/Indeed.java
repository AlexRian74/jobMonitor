package pages;

import java.util.List;
import java.util.TreeSet;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;

import datatypes.JobList;
import datatypes.Settings;
import datatypes.WaitType;
import utils.Filter;
import utils.LoadSettings;
import utils.Storage;

public class Indeed extends BaseDriver {

	final String SEARCH_TEXTBOX = "//input[@id='text-input-what']";
	final String LOCATION_TEXTBOX = "//input[@id='text-input-where']";
	final String GO_BUTTON = "//button[@type='submit']";

	// all the job cards
	final String JOB_CARDS = "//*[@class='resultContent']";
	// childrens of a particular JOB_CARD
	final String JOB_TITLE = ".//h2";
	final String COMPANY_NAME = "./div[2]/div/span";
	final String LINK = ".//a";

	final String JOB_DESCRIPTION = "//div[@id='jobDescriptionText']";
	final String JOB_COLUMN = "//*[@id='mosaic-provider-jobcards']";

	final String FILTER_DATE = "//div[normalize-space()='Date posted']";
	// Last 24 hours//Last 3 days//Last 7 days//Last 14 days
	final String FILTER_DATE_MENU = "//*[@id='filter-dateposted-menu']";

	final String FILTER_JOB_TYPE = "//div[normalize-space()='Job type']";
	// Full-time//Contract//Part-time//Temporary//Internship
	final String FILTER_JOB_TYPE_MENU = "//ul[@id='filter-jobtype-menu']";

	final String FILTER_POSTED_BY = "//button[@id='filter-srctype']";
	// Employer//Staffing agency
	final String FILTER_POSTED_BY_MENU = "//*[@id='filter-srctype-menu']";

	final String FILTER_RADIUS = "//button[@id='filter-radius']";
	// Employer//Staffing agency
	final String FILTER_RADIUS_MENU = "//ul[@id='filter-radius-menu']";

	final String FILTER_EXPERIENCE_LEVEL = "//button[@id='filter-explvl']";
	// Mid Level//Entry Level//Senior Level//No Experience Required
	final String FILTER_EXPERIENCE_LEVEL_MENU = "//*[@id='filter-explvl-menu']";
	// next_page_btn (absent on the last page)
	final String NEXT_PAGE_BUTTON = "//a[@aria-label='Next Page']//*[name()='svg']";

	// this button appears when ineed requires human confirmation
	final String HUMAN_CHECK_BUTTON = "//map/area";
	// jobs found string
	final String TOTAL_JOBS_FOUND = "(//div[starts-with(@class, 'jobsearch-JobCount')])[last()]/span[1]";

	// verify you're a human

	// settings from .properties
	public boolean run = false;
	private String jobLevel, jobAge, jobType, postedBy, jobRadius;
	private String[] locations, keywords;
	private int indeed_limit;
	private TreeSet<JobList> jobList = new TreeSet<>();
	private int windowPosition;
	public String currentKeyword;

	public Indeed(int windowPosition) {
		// initiate all the process
		log.info("Starting Indeed.com parser");
		loadSettings();
		if (run == false) {
			log.info("execution of indeed is disabled");
			return;
		}
		this.windowPosition = windowPosition;

		super.setUp("https://www.indeed.com/", windowPosition);
		int cardsAdded = 0;
		first: for (String city : locations) {
			for (String keyword : keywords) {
				log.info("Searching for " + keyword + " in " + city);
				currentKeyword = keyword;
				try {
					if (!setFilter(currentKeyword, city)) {
						log.info("Unable to set 'jobAge' or 'jobLevel'. This keyword will be skipped");
						continue;
					}
					flipPages();
				} catch (NoSuchWindowException e) {
					log.error("The browser was closed");
					break first;
				} catch(Exception e) {
					log.error(e.toString());
				}
				Filter.removeGarbage(jobList);
				getDescriptions();
				cardsAdded =cardsAdded + Storage.addToStorage(jobList, true);
				jobList.clear();
			}
		}

		log.info("Added cards from Indeed: " + cardsAdded);
	}

	public void getDescriptions() {
		log.info("Parsing descriptions");
		for (JobList card : jobList) {
			if (card.jobDescription != null) {
				continue;
			}
			driver.get(card.url);
			boolean flag = true;
			while (flag) {
				if (driver.getTitle().equals("Just a moment...")) {
					close();
					sleep(5000);
					setUp(card.url, windowPosition);
				} else {
					flag = false;
				}
			}

			for (int i = 3; i > 0; i--) {
				try {
					card.jobDescription = getElement(JOB_DESCRIPTION, WaitType.PRESENCE).getText();
					break;
				} catch (Exception e) {
					// in case of an error, retry
					close();
					setUp(card.url, windowPosition);
				}
			}
		}
	}

	public void flipPages() {
		// in case no jobs were found
		WebElement total_jobs_found = getElement(TOTAL_JOBS_FOUND, WaitType.PRESENCE);
		if (total_jobs_found == null) {
			return;
		}
		String s = total_jobs_found.getText();
		int jobFound = Integer.parseInt(s.replaceAll("[^0-9]+", ""));
		if (jobFound == 0) {
			return;
		}

		int jobAdded = 0;
		do {
			// jobList contains only unique cards
			jobAdded = getLinksFromCurrentPage(jobAdded);
			log.info("Gathered cards: " + jobAdded);
			// here we evaluate if we can go to the next page
			WebElement nextPage = getElement(NEXT_PAGE_BUTTON, WaitType.PRESENCE);
			if (nextPage == null) {
				break;
			}
			if (jobAdded < indeed_limit)
				nextPage.click();

		} while (jobAdded < indeed_limit);

	}

	public int getLinksFromCurrentPage(int counter) {
		WebElement jobColumn = getElement(JOB_COLUMN, WaitType.PRESENCE);
		if (jobColumn == null) {
			return 0;
		}
		List<WebElement> jobCards = jobColumn.findElements(By.xpath(JOB_CARDS));
		for (WebElement jobCard : jobCards) {
			if (counter == indeed_limit) {
				log.info("Search limit is reached");
				return counter;
			}
			String jobTitle = getChildElement(JOB_TITLE, jobCard).getText().trim();
			String applyLink = getChildElement(LINK, jobCard).getAttribute("href");
			String companyName = getChildElement(COMPANY_NAME, jobCard).getText().trim();
			jobList.add(new JobList(jobTitle, companyName, null, applyLink, currentKeyword));
			counter++;
		}
		return counter;
	}

	public void loadSettings() {
		Settings settings = LoadSettings.getSettings("Indeed");
		jobType = settings.jobType.contains("Not") ? "" : settings.jobType;
		jobRadius = settings.jobRadius.contains("Not") ? "" : settings.jobRadius;
		jobLevel = settings.jobLevel.contains("Not") ? "" : settings.jobLevel;
		postedBy = settings.postedBy.contains("Not") ? "" : settings.postedBy;
		jobAge = settings.timePosted;
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		locations = settings.locations != null ? settings.locations : Storage.locations;
		indeed_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		run = settings.run;

	}

	public boolean setFilter(String what, String where) {
		// sets filter on job search
		WebElement searchTextbox = getElement(SEARCH_TEXTBOX, WaitType.PRESENCE);
		eraseText(searchTextbox);
		sleep(500);
		typeText(searchTextbox, what);

		WebElement location = getElement(LOCATION_TEXTBOX, WaitType.CLICKABLE);
		eraseText(location);
		typeText(location, where);
		sleep(1000);
		getElement(GO_BUTTON, WaitType.CLICKABLE).click();
		// set filters
		if ((!setFilterOption(jobAge, FILTER_DATE, FILTER_DATE_MENU) && !jobAge.isBlank())
				|| (!setFilterOption(jobLevel, FILTER_EXPERIENCE_LEVEL, FILTER_EXPERIENCE_LEVEL_MENU)
						&& !jobLevel.isBlank())) {
			return false;
		}

		setFilterOption(jobType, FILTER_JOB_TYPE, FILTER_JOB_TYPE_MENU);
		setFilterOption(postedBy, FILTER_POSTED_BY, FILTER_POSTED_BY_MENU);
		setFilterOption(jobRadius, FILTER_RADIUS, FILTER_RADIUS_MENU);
		return true;
	}

	// option - text from drop down menu, dropdownXPath and menuXPath that appears
	// when you click on dropdownXPath

	public boolean setFilterOption(String option, String dropdownXPath, String menuXPath) {
		if (option.isBlank())
			return false;
		log.info("Setting filter option " + option);
		// trying to set the option count times
		boolean flag = true;
		short count = 2;
		while (flag && count > 0) {
			try {
				getElement(dropdownXPath, WaitType.CLICKABLE).click();
				WebElement menu = getElement(menuXPath, WaitType.PRESENCE);
				menu.findElement(By.xpath("." + CONTAINS_TEXT.replace("value", option))).click();
				flag = false;
			} catch (Exception e) {
				count--;
			}
		}

		if (count > 0) {
			return true;
		} else {
			log.info("unable to set " + option);
			return false;
		}
	}

}
