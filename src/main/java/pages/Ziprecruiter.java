package pages;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import datatypes.JobList;
import datatypes.Settings;

import utils.Filter;
import utils.LoadSettings;
import utils.Storage;
import datatypes.WaitType;

public class Ziprecruiter extends BaseDriver {
	static private final String SEARCH_TEXTBOX = "(//input[@name='search'])[x]";
	static private final String SEARCH_LOCATION = "(//input[@name='location'])[x]";
	static private final String SEARCH_BUTTON = "(//*[@class='actions']/button)[1]";
	static private final String EXPIRED = "//div[contains(@class,'expired')]";
	static private final String JOBS_FOUND = "(//div/h1)[1]";
	static private final String NEXT_PAGE = "//a[@title='Next Page']";
	static private final String LAST_PAGE = "(//div[@class='h-48 w-full']//div)[last()]";
	static private final String LOAD_MORE = "//*[@id='job_postings_skip']/div/button";
	// one page verison
	static private final String JOB_TITLE_S = "//div/h2/a";

	// scrollable version
	static private final String JOB_CARDS = "//div[@class='job_content']";
	static private final String JOB_TITLE = ".//a[@class='job_link']";

	// page version
	static private final String JOB_TITLE_P = "//h2/a";

	// individual job page
	static private final String COMPANY_NAME = "//a[@class='hiring_company']";
	static private final String JOB_DESCRIPTION = "//div[@class='job_description']";

	static private final String FILTER_DAYS = "//li[@data-filter='days']/button";
	static private final String FILTER_DAYS_OPTION = "//li[@data-filter='days']//button[@role='menuitemradio'][x]";

	static private final String FILTER_DISTANCE = "//div[@aria-label='distance']/../button";
	static private final String FILTER_DISTANCE_OPTION = "//div[@aria-label='distance']//button[x]";

	static private final String FILTER_EMPL_TYPE = "//div[@aria-label='employment type']/../button";
	static private final String FILTER_EMPL_TYPE_OPTION = "//div[@aria-label='employment type']//div[2]/button[x]";

	private TreeSet<JobList> jobList = new TreeSet<>();
	private TreeSet<JobList> rejectedList = new TreeSet<>();
	private String currentKeyword;
	private int jobFound = 0;
	// maximum amount of cards for a single search

	// variables from .properties
	private boolean run;
	private String[] locations;
	private String[] keywords;
	int jobType, jobAge, jobRadius = -1;
	private int zip_limit;
	private int windowPosition;

	public void closePopup() {
		Actions actions = new Actions(driver);
		actions.sendKeys(Keys.ESCAPE).perform();
	}

	public Ziprecruiter(int windowPosition) {
		String url = "https://www.ziprecruiter.com/jobs-search?form=jobs-landing&search=";
		loadSettings();
		if (run == false) {
			log.info("execution of Ziprecruiter is disabled");
			return;
		}
		this.windowPosition = windowPosition;
		setUp(url, windowPosition);
		int cardsAdded = 0;
		first: for (String city : locations) {
			for (String keyword : keywords) {
				currentKeyword = keyword;
				try {
					if (!getPage(url)) {
						close();
						setUp(url, windowPosition);
					}
					if (driver.getTitle().toLowerCase().contains("moment...")
							|| driver.getTitle().toLowerCase().contains("went wrong")) {
						close();
						setUp(url, windowPosition);
					}
					log.info("Searching for " + keyword + " in " + city);

					if (!setFilter(keyword, city)) {
						log.info("Unable to set 'jobAge' or 'jobType'. This keyword will be skipped");
						continue;
					}
					closePopup();
					getJobTitles();
					Filter.removeGarbage(jobList);
					parseCards();
					cardsAdded = cardsAdded + Storage.addToStorage(jobList, true);
					Storage.addToStorage(rejectedList, false);
					jobList.clear();
					rejectedList.clear();
				} catch (NoSuchWindowException e) {
					log.error("The browser was closed");
					break first;
				} catch (NoSuchSessionException e) {
					log.error(e.toString());
					break first;
				}
				catch (Exception e) {
					log.error(e.toString());
				}

			}
		}

		log.info("Added cards from ZipRecruiter: " + cardsAdded);
	}

	private void parseCards() {
		Iterator<JobList> iterator = jobList.iterator();
		while (iterator.hasNext()) {
			JobList j = iterator.next();
			String[] tmp = getDescription(j.url);
			if (tmp == null) {
				JobList rejected = new JobList(j.jobTitle, "n/a", "n/a", j.url, j.searchKeyword);
				rejected.declineReason = "External link";
				rejectedList.add(rejected);
				iterator.remove();
			} else {
				j.jobDescription = tmp[1];
				j.companyName = tmp[0];
			}
		}

	}

	private void getJobTitles() {
		// below we have two variants: one-long page site or
		// several pages. They appear at random
		// this code tries to find loadMore button and click on it
		WebElement loadMore = getElement(LOAD_MORE, WaitType.CLICKABLE);
		if (loadMore != null) {
			loadMore.click();
			scrollPage(JOB_CARDS, jobFound);
			// for single long scrollable page we have its own job_title
			getCardsFromSinglePage();
			return;// no need for further actions withing this method
		}

		// This code attempts to locate the 'Next Page' button. If it can't find it, it
		// handles a short single page.
		WebElement nextPage = getElement(NEXT_PAGE, WaitType.PRESENCE);
		if (nextPage != null) {
			getCardsFromPages();
		} else {
			// for short single page we have its own job_title
			scrollPage(JOB_CARDS, jobFound);
			getCardsFromSinglePage();
		}

	}

	public boolean setFilter(String keyword, String city) {
		log.info("Setting filters");
		js.executeScript("window.scrollTo(0, 0);");
		// here we have constantly changing xpaths due to two different versions of
		// ziprecruiter
		for (int i = 1; i <= 2; i++) {
			String search_textbox = SEARCH_TEXTBOX.replaceAll("x", String.valueOf(i));
			String search_location = SEARCH_LOCATION.replaceAll("x", String.valueOf(i));
			try {
				WebElement searchTextbox = driver.findElement(By.xpath(search_textbox));
				WebElement searchLocation = driver.findElement(By.xpath(search_location));
				if (searchTextbox == null || searchLocation == null) {
					continue;
				}
				js.executeScript("arguments[0].value=arguments[1]", searchTextbox, keyword);
				js.executeScript("arguments[0].value=arguments[1]", searchLocation, city);

				// go to search button and click on it
				WebElement searchButton = getElement(SEARCH_BUTTON, WaitType.PRESENCE);
				Actions actions = new Actions(driver);
				actions.moveToElement(searchLocation).click().perform();

				searchButton.click();
				break; // exit from the loop on success
			} catch (Exception e) {

			}
		}
		jobFound = 0;
		closePopup();
		setFilterOption(FILTER_DISTANCE, jobRadius);
		// if we can't set critical filters, return false
		if (!setFilterOption(FILTER_DAYS, jobAge) || !setFilterOption(FILTER_EMPL_TYPE, jobType)) {
			log.info("unable to set filter");
			return false;
		}

		// When all filters are set, we store the quantity of found jobs
		WebElement jf = getElement(JOBS_FOUND, WaitType.PRESENCE);
		if (jf != null) {
			jobFound = Integer.parseInt(jf.getText().replaceAll("[a-zA-Z,]", "").trim());
		}
		if (jobFound > 0) {
			// setting limit (only first CARDS_LIMIT will be processed)
			jobFound = jobFound > zip_limit ? zip_limit : jobFound;
			return true;
		}
		return false;
	}

	public void loadSettings() {
		Settings settings = LoadSettings.getSettings("Ziprecruiter");
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		locations = settings.locations != null ? settings.locations : Storage.locations;
		zip_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		run = settings.run;
		switch (settings.timePosted) {
		case "5 Days":
			jobAge = 4;
			break;
		case "1 Day":
			jobAge = 5;
			break;
		default:
			jobAge = -1;
		}

		switch (settings.jobType) {
		case "Full-time":
			jobType = 2;
			break;
		case "Contract":
			jobType = 3;
			break;
		case "Part-time":
			jobType = 4;
			break;
		default:
			jobType = -1;
		}

		switch (settings.jobRadius) {
		case "25 Miles":
			jobRadius = 4;
			break;
		case "50 Miles":
			jobRadius = 5;
			break;
		case "100 Miles":
			jobRadius = 6;
			break;
		default:
			jobType = -1;
		}

	}

	private boolean setFilterOption(String menuBtn, int option) {
		String xpath = null;
		if (option == -1) {
			return true;
		}
		switch (menuBtn) {
		case FILTER_DAYS:
			xpath = FILTER_DAYS_OPTION.replaceAll("x", "" + option);
			log.info("Trying to set job age");
			break;
		case FILTER_DISTANCE:
			xpath = FILTER_DISTANCE_OPTION.replaceAll("x", "" + option);
			log.info("Trying to set distance");
			break;
		case FILTER_EMPL_TYPE:
			xpath = FILTER_EMPL_TYPE_OPTION.replaceAll("x", "" + option);
			log.info("Trying to set job type");
			break;
		default:
			log.info("check ziprecruiter.properties, incorrect value");
			return false;
		}

		WebElement menu;
		for (int i = 2; i > 0; i--) {
			try {
				menu = getElement(menuBtn, WaitType.CLICKABLE);
				if (menu == null)
					return false;
				menu.click();
				// sleep(300);
				WebElement menuOption = getElement(xpath, WaitType.CLICKABLE);
				if (menuOption == null)
					return false;
				menuOption.click();
				// sleep(1000);
				break;
			} catch (Exception e) {
				closePopup();
			}
		}

		return true;
	}

	private void getCardsFromSinglePage() {
		List<WebElement> jobTitleElements = null;
		String jobTitleXPath = JOB_TITLE_S;
		for (short i = 0; i < 2; i++) {
			try {
				jobTitleElements = getElements(jobTitleXPath);
				if (jobTitleElements == null) {
					jobTitleElements = getElements(JOB_TITLE);
				}
				break;
			} catch (Exception e) {
				jobTitleXPath = JOB_TITLE;
			}
		}
		if (jobTitleElements == null) {
			log.info("getCardsFromSinglePage(): Can't find titles on the page");
			return;
		}
		int count = 0;
		log.info("Gathering cards");
		for (int i = 0; i < jobFound; i++) {
			WebElement jTitle = jobTitleElements.get(i);
			String title = jTitle.getText().replaceAll("NEW!", "").trim();
			String href = jTitle.getAttribute("href");

			jobList.add(new JobList(title, "", "", href, currentKeyword));
			count++;
		}
		log.info(count + " cards gathered");
	}

	private void getCardsFromPages() {
		String lastPage = getElement(LAST_PAGE, WaitType.PRESENCE).getText();
		int totalPages = Integer.parseInt(lastPage);
		int cardsProcessed = 0;
		for (int i = 1; i <= totalPages; i++) {
			boolean flag = true;
			short retry = 3;
			String currentUrl = null;
			do {
				try {
					currentUrl = driver.getCurrentUrl();
					WebElement nextPage = getElement(NEXT_PAGE, WaitType.CLICKABLE);
					List<WebElement> jobCards = getElements(JOB_TITLE_P);
					log.info("Gathering cards");
					for (WebElement card : jobCards) {
						if (cardsProcessed > zip_limit) {
							return;
						}
						String title = card.getText().replaceAll("NEW!", "").trim();
						String href = card.getAttribute("href");

						jobList.add(new JobList(title, "", "", href, currentKeyword));
						cardsProcessed++;
					}
					log.info("Cards gathered: " + cardsProcessed);
					if (i != totalPages) {
						nextPage.click();
						sleep(1000);
						log.info("Next page");
						if (driver.getTitle().toLowerCase().contains("moment...")) {
							close();
							setUp(currentUrl, windowPosition);
							closePopup();
							getElement(NEXT_PAGE, WaitType.CLICKABLE).click();
						}
					}
					flag = false;
				} catch (Exception e) {
					retry--;
					closePopup();
				}
			} while (flag && retry > 0);
		}

	}

	public String[] getDescription(String url) {
		String description = null;
		String companyName = null;
		getPage(url);

		// we can't process external links
		if (!driver.getCurrentUrl().contains("ziprecruiter.com")) {
			return null;
		}
		if (driver.getTitle().toLowerCase().contains("moment...")) {
			close();
			setUp(url, windowPosition);
		}
		if (driver.getTitle().toLowerCase().contains("not found")) {
			return null;
		}
		if (getElement(EXPIRED, WaitType.PRESENCE) != null) {
			return null;
		}

		WebElement dElement = getElement(JOB_DESCRIPTION, WaitType.PRESENCE);
		WebElement nElement = getElement(COMPANY_NAME, WaitType.PRESENCE);
		if (dElement == null || nElement == null) {
			return null;
		}
		try {
			description = dElement.getText().trim();
			companyName = nElement.getText().trim();
		} catch (Exception e) {
			log.error(e.toString());
			return null;
		}
		return new String[] { companyName, description };
	}

}
