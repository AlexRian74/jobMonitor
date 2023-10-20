package pages;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;
import datatypes.JobList;
import datatypes.Settings;
import datatypes.WaitType;
import utils.Filter;
import utils.LoadSettings;
import utils.Storage;

public class Glassdoor extends BaseDriver {
	// settings from .properties
	private String jobType;
	private String jobAge;
	private boolean run = false;
	public String[] searchArea;
	public String[] keywords;
	public int glass_limit;

	public String currentKeyword;
	private TreeSet<JobList> jobList = new TreeSet<>();

	private int jobFound = 0;
	// All found jobs here:

	// xpaths
	final String SEARCH_BUTTON = "//button[@data-test='search-button']";
	final String SEARCH_INPUT = "//input[@data-test='keyword-search-input']";// "//input[@aria-label='Search keyword']";
	final String LOCATION_INPUT = "//input[@data-test='location-search-input']";// "//input[@aria-label='Search
																				// location']";

	final String FILTER_START_BUTTON = "(//button[@aria-label='Open filter menu'])[2]";
	final String FILTER_BUTTON = "(//div[contains(@class,'SearchFiltersBar')]//button[contains(text(),'name')])[1]";
	// name should be in lower case
	final String FILTER_OPTION = "//div[contains(@class,'SearchFiltersBar_dropdown')]//ul/li/button//div[contains(translate(text(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'name')]";
	final String FILTER_APPLY = "//button//span[contains(text(),'Apply filters')]/../..";
	final String SHOW_MORE_JOBS = "(//span[contains(text(),'Show more jobs')]//..//..)[1]";

	final String JOB_TITLE = "//a[contains(@id,'job-title')]";
	final String JOB_COUNT = "//div/h1";

	// job description and company name from an individual job page
	final String JOB_DESCRIPTION = "//div[@id='JobDescriptionContainer']";
	final String COMPANY_NAME = "//div[@data-test='employer-name']//div";

	public Glassdoor(int windowPosition) {
		loadSettings();
		if (run == false) {
			log.info("execution of glassdor is disabled");
			return;
		}
		String startUrl = "https://www.glassdoor.com/Job/";
		super.setUp(startUrl, windowPosition);
		int cardsAdded = 0;
		// for each place we are seeking keywords
		first: for (String city : searchArea) {
			for (String keyword : keywords) {
				// setting current keyword, it will be used when adding jobCard to a jobList
				currentKeyword = keyword;
				jobFound = 0; // reset job counter
				
				if(driver.getTitle().equals("Security | Glassdoor")) {
					driver.navigate().refresh();
				}
				
				try {
					getPage(startUrl);
					for (int i = 2; i > 0; i--) {
						if (setFilter(keyword, city))
							break;
					}
					
					if (jobFound > 0) {
						flipPages();
						Filter.removeGarbage(jobList);
						getDescriptions();
						cardsAdded = cardsAdded + Storage.addToStorage(jobList, true);
						jobList.clear();
						startUrl = driver.getCurrentUrl();
					}
				} catch (NoSuchWindowException e) {
					log.error("The browser was closed");
					break first; // exiting the loop
				} catch (Exception e) {
					log.error(e.toString());
				}
			}
		}

		log.info("Added cards from Glassdoor: " + cardsAdded);
	}

	public void getDescriptions() {
		log.info("parsing started");
		Iterator<JobList> iterator = jobList.iterator();
		while (iterator.hasNext()) {
			JobList jobCard = iterator.next();

			boolean flag = true;
			short count = 3;
			while (flag && count > 0) {
				try {
					// using jsoup to open job link
					Document document = Jsoup.connect(jobCard.url).userAgent("Opera").ignoreHttpErrors(true)
							.referrer("http://www.google.com").get();
					// removing all html, css tags and spaces
					jobCard.jobDescription = removeTags(document.selectXpath(JOB_DESCRIPTION).html());
					jobCard.companyName = removeTags(document.selectXpath(COMPANY_NAME).html())
							.replaceAll("[.,\\d]|[^\\p{ASCII}]", "").trim();
					if(!jobCard.companyName.isBlank() && !jobCard.jobDescription.isBlank())
						flag = false;
					count--;
				} catch (IOException e) {
					count--;
					log.error(e.toString() + " will try again " + count + " times");
				}
			}
			if (count == 0)
				iterator.remove(); // in case we didn't get description or company name
		}
		log.info("parsing finished");
		;
	}

	public void loadSettings() {
		Settings settings = LoadSettings.getSettings("Glassdoor");
		jobType = settings.jobType.contains("Not") ? "" : settings.jobType;
		jobAge = settings.timePosted;
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		searchArea = settings.locations != null ? settings.locations : Storage.locations;
		glass_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		run = settings.run;
	}

	public boolean setFilter(String keywords, String place) {
		if (driver == null) {
			return false;
		}
		getElement(SEARCH_BUTTON, WaitType.CLICKABLE).click();
		// set a search keyword
		WebElement searchTextbox = getElement(SEARCH_INPUT, WaitType.CLICKABLE);
		if (searchTextbox == null)
			return false;
		js.executeScript("arguments[0].value = '';", searchTextbox);
		typeText(searchTextbox, keywords);

		// Enter location and submit the search
		WebElement loc = getElement(LOCATION_INPUT, WaitType.CLICKABLE);
		js.executeScript("arguments[0].value = '';", loc);
		typeText(loc, place);
		loc.sendKeys(Keys.RETURN);
		// waiting for the page to be refreshed
		sleep(1000);
		log.info("Searching for: " + keywords + " in " + place);

		// setting filter options
		getElement(FILTER_START_BUTTON, WaitType.CLICKABLE).click();

		String filterBtn = FILTER_BUTTON.replaceFirst("name", "Job types");
		if (!setFilterOption(filterBtn, jobType) && !jobType.isBlank()) {
			log.info("Unable to set type filter, exiting");
			jobFound = 0;
			return false;
		}
		filterBtn = FILTER_BUTTON.replaceFirst("name", "Date posted");
		if (!setFilterOption(filterBtn, jobAge) && !jobAge.isBlank()) {
			log.info("Unable to set time filter, exiting");
			jobFound = 0;
			return false;
		}

		getElement(FILTER_APPLY, WaitType.CLICKABLE).click();
		sleep(2000);
		// Extract and log the number of found jobs
		try {
			WebElement jobCnt = getElement(JOB_COUNT, WaitType.PRESENCE);
			if (jobCnt.isDisplayed()) {
				Pattern pattern = Pattern.compile("\\d+");
				Matcher matcher = pattern.matcher(jobCnt.getText());
				if (matcher.find()) {
					String val = matcher.group();
					jobFound = Integer.parseInt(val);
					log.info("Glassdoor says found jobs: " + jobFound);
					log.info("Limit set to: " + glass_limit);
				} else {
					log.info("No jobs found");
					return false;
				}
			}
		} catch (Exception e) {
			log.info("No jobs found");
			return false;
		}
		return true;
	}

	public boolean setFilterOption(String optionXPath, String optionText) {
		if (optionText == null || optionText.isBlank()) {
			return false;
		}

		WebElement menu = getElement(optionXPath, WaitType.CLICKABLE);
		if (menu == null) {
			return false;
		}
		js.executeScript("arguments[0].scrollIntoView(true);", menu);
		menu.click();

		// making xpath for the dropdown element 'option'
		String xpath = FILTER_OPTION.replaceFirst("name", optionText.toLowerCase());

		WebElement option = getElement(xpath, WaitType.CLICKABLE);
		if (option == null) {
			return false;
		}
		js.executeScript("arguments[0].scrollIntoView(true);", option);
		// clicking on the option
		option.click();

		return true;
	}

	public void getLinksFromCurrentPage() {

		List<WebElement> jobCards = getElements(JOB_TITLE);

		int limit = jobFound > glass_limit ? glass_limit : jobFound;
		int startSize = jobList.size();
		// going through each job card
		for (int i = 0; i < limit; i++) {
			log.info("card # " + (i + 1));
			String href = null;
			String companyName = "n/a";
			String jobTitle = null;

			try {
				// picking one job card with index i and extracting info from it
				WebElement jobCard = jobCards.get(i);
				js.executeScript("arguments[0].scrollIntoView(true);", jobCard);
				href = jobCard.getAttribute("href");
				jobTitle = jobCard.getText().trim();

			} catch (Exception e) {
				log.error(e.toString());
			}

			// adding new element to the jobList
			if (jobTitle != null && companyName != null && href != null) {
				jobList.add(new JobList(jobTitle, companyName, null, href, currentKeyword));
			}
		}
		log.info("Unique cards found " + (jobList.size() - startSize));
	}

	public void flipPages() {
		if (driver == null || jobFound == 0) {
			return;
		}
		boolean nextPageFlag = true;
		int limit = jobFound > glass_limit ? glass_limit : jobFound;
		do {
			closePopup();
			List<WebElement> jobCards = getElements(JOB_TITLE);
			if (jobCards.size() >= limit)
				nextPageFlag = false;

			WebElement showMore = getElement(SHOW_MORE_JOBS, WaitType.CLICKABLE);
			if (showMore != null && nextPageFlag) {
				js.executeScript("arguments[0].scrollIntoView(true);", showMore);
				showMore.click();
				// wait for cards to be loaded
				boolean waitFlag = true;
				long startTime = System.currentTimeMillis();
				while (waitFlag) {
					String state = showMore.getAttribute("data-loading");
					if (state == null)
						break;
					if (state.equals("false"))
						break;
					if (System.currentTimeMillis() - startTime > 10000) {
						break;
					}
				}

			} else {
				nextPageFlag = false;
			}

		} while (nextPageFlag);
		getLinksFromCurrentPage();
	}

	public void closePopup() {
		driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
	}

}
