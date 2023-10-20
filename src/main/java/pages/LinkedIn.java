package pages;

import java.util.LinkedHashMap;
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

public class LinkedIn extends BaseDriver {
	private final String LOGIN = "//*[@id='session_key']";
	private final String PASSWORD = "//*[@id='session_password']";
	private final String LOGIN_BTN = "//button[contains(text(),'Sign in')]";

	private final String JOBS_FOUND = "//div[@class='jobs-search-results-list__subtitle']";

	private final String JOB_TITLES = "//ul[@class='scaffold-layout__list-container']/li/div/div/div/div[2]/div/a";
	private final String COMPANY_NAME = "//div[contains(@class, 'card__primary-description')]//a[@class='app-aware-link ']";
	private final String COMPANY_NAME_PREMIUM = "//div[@class='jobs-unified-top-card__primary-description']/div";
	private final String JOB_DESCRIPTION = "//div[@id='job-details']/span";
	// pages available from 2 till the one before the last
	private final String PAGES = "//li[@data-test-pagination-page-btn]";
	private final String SEE_MORE = "//button[@aria-label='Click to see more description']";

	// .properties variables
	private boolean run;
	private String[] keywords;
	private String[] locations;
	private String jobAge = null;
	private String sortBy = null;
	private String jobLevel = null;
	private String username = "";
	private String password = "";
	private int linkedin_limit;
	private int startPause;
	private String startURL = "https://www.linkedin.com/jobs/search/?keywords=X&location=Y";
	private String currentKeyword;
	private int jobFound = 0;
	private TreeSet<JobList> jobList = new TreeSet<>();

	public LinkedIn(int windowPosition) {
		// initiate all the process
		log.info("Starting linkedin.com parser");
		loadSettings();
		if (run == false) {
			log.info("execution of linkedin is disabled");
			return;
		}

		super.setUp("https://www.linkedin.com", windowPosition);

		if (username.isEmpty() || password.isEmpty()) {
			log.info("Please log in into LinkedIn. You have " + startPause + " sec");
			for (int i = startPause; i > 0; i--) {
				log.info(i);
				sleep(1000);
			}
		} else {
			login();
		}

		// check if the user logged in
		String title = driver.getTitle();
		if (title.toLowerCase().contains("log") || title.toLowerCase().contains("check")) {
			log.info("Login wasn't successful. Exiting.");
			return;
		}
		int cardsAdded = 0;
		first: for (String location : locations) {
			for (String keyword : keywords) {
				log.info("searching for " + keyword + " in " + location);
				currentKeyword = keyword;
				String url = startURL.replaceFirst("X", keyword).replaceFirst("Y", location);
				for (int i = 3; i > 0; i--) {
					try {
						driver.get(setFilter(url));
						findJobs();
						break;
					} catch (NoSuchWindowException e) {
						log.error("The browser was closed");
						break first;
					} catch (Exception e) {
						log.error(e.getMessage());
					}
				}
				Filter.removeGarbage(jobList);
				getDescriptions();
				cardsAdded = cardsAdded + Storage.addToStorage(jobList, true);
				jobList.clear();
			}
		}

		log.info("Added cards from LinkedIn: " + cardsAdded);
	}

	private void login() {
		for (int i = 3; i > 0; i--) {
			try {
				WebElement login = getElement(LOGIN, WaitType.CLICKABLE);
				typeText(login, username);
				WebElement passw = getElement(PASSWORD, WaitType.CLICKABLE);
				typeText(passw, password);
				WebElement loginBtn = getElement(LOGIN_BTN, WaitType.CLICKABLE);
				loginBtn.click();
				break;
			} catch (Exception e) {

			}
		}
	}

	private void getDescriptions() {
		for (JobList card : jobList) {
			for (short i = 3; i > 0; i--) {
				try {
					driver.get(card.url);
					getElement(SEE_MORE, WaitType.CLICKABLE).click();
					card.jobDescription = getElement(JOB_DESCRIPTION, WaitType.PRESENCE).getText();

					WebElement tmp = getElement(COMPANY_NAME, WaitType.PRESENCE);
					if (tmp == null) {
						tmp = getElement(COMPANY_NAME_PREMIUM, WaitType.PRESENCE);
					}

					if (tmp == null) {
						card.companyName = "N/A";
					} else {
						card.companyName = tmp.getText().trim();
					}
					log.info("parsed " + card.jobTitle);
					break;
				} catch (Exception e) {
					log.error(e.getMessage());
				}
			}
		}

	}

	private void findJobs() {
		WebElement jobsFound = getElement(JOBS_FOUND, WaitType.PRESENCE);
		if (jobsFound == null) {
			log.info("no jobs were found");
			return;
		} else {
			String found = jobsFound.getText().replaceAll("[^0-9]+", "");
			log.info("jobs found " + found);
			jobFound = Integer.parseInt(found);
		}
		int currentPage = 0;

		// scroll the cards
		int limit = jobFound > linkedin_limit ? linkedin_limit : jobFound;
		log.info("search limit " + linkedin_limit);

		boolean flag = true;
		// counts gathered cards
		int counter = 0;
		while (flag) {
			scrollPage(JOB_TITLES, limit);
			List<WebElement> cards = driver.findElements(By.xpath(JOB_TITLES));

			for (WebElement card : cards) {
				if (counter == limit)
					break;
				String title = card.getText().trim();
				String url = card.getAttribute("href");
				jobList.add(new JobList(title, "", null, url, currentKeyword));
				counter++;
			}

			List<WebElement> pages = getElements(PAGES);
			if (pages != null && counter < limit) {
				if ((currentPage < pages.size() - 1) && counter < limit) {
					currentPage++;
					// if next page link has digits, click on it
					if (!pages.get(currentPage).getText().replaceAll("[^0-9]+", "").isBlank()) {
						pages.get(currentPage).click();
						continue;
					}
				}
			}
			log.info("Cards added: " + counter);
			flag = false;
		}

	}

	private void loadSettings() {
		Settings settings = LoadSettings.getSettings("LinkedIn");
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		locations = settings.locations != null ? settings.locations : Storage.locations;
		jobLevel = settings.jobLevel.contains("Not") ? "" : settings.jobLevel;
		linkedin_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		startPause = settings.delay == 0 ? 40 : settings.delay;
		run = settings.run;

		LinkedHashMap<String, String> credentials = LoadSettings
				.getPairs(new String[] { "LinkedIn_username", "LinkedIn_password" });
		username = credentials.getOrDefault("LinkedIn_username", "").strip();
		password = credentials.getOrDefault("LinkedIn_password", "").strip();

		switch (settings.timePosted) {
		case "Last Day":
			jobAge = "&f_TPR=r86400";
			break;
		case "Last Week":
			jobAge = "&f_TPR=r604800";
			break;
		default:
			jobAge = "&f_TPR=r86400";
		}
		switch (settings.sortBy) {
		case "Most relevant":
			sortBy = "&sortBy=R";
			break;
		case "Most recent":
			sortBy = "&sortBy=DD";
			break;
		default:
			sortBy = "&sortBy=DD";
		}
		if (!jobLevel.isBlank()) {
			jobLevel = "&f_E=" + settings.jobLevel.replaceAll(",", "%2C");
		}

	}

	private String setFilter(String url) {
		url = url + jobAge + jobLevel + sortBy;
		return url;

	}

}
