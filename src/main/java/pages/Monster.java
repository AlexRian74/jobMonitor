package pages;

import java.util.List;
import java.util.TreeSet;

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

public class Monster extends BaseDriver {
	private String jobType;
	private String jobAge;
	private String jobRadius;
	private boolean run = false;
	public String[] locations;
	public String[] keywords;
	public int monster_limit;
	public String currentKeyword;

	private TreeSet<JobList> jobList = new TreeSet<>();

	private final String JOB_TITLE = "//*[@data-testid='jobTitle']";
	private final String COMPANY_NAME = "following-sibling::span";
	private final String JOB_DESCRIPTION = "//*[@id=\"jobview-container\"]//h2/../div[1]";
	private final String NO_MORE_RESULTS = "//span[contains(text(),'No More Results')]";
	private final String blankURL = "https://www.monster.com/jobs/search?q=keyword&where=location&page=1&et=&recency=&rd=";
	private String currentURL = "";

	public Monster(int windowPosition) {
		log.info("Starting Monster.com parser");
		loadSettings();
		if (run == false) {
			log.info("execution of monster is disabled");
			return;
		}
		super.setUp(blankURL, windowPosition);
		int cardsAdded = 0;
		first: for (String location : locations) {
			keywordLoop: for (String keyword : keywords) {
				currentKeyword = keyword;
				log.info("Searching for " + keyword + " in " + location);
				// open search page with filters set up
				// on success we break this loop
				// if cards were not found, we continue keywordLoop
				for (int i = 3; i > 0; i--) {
					try {
						driver.get(setURL(location, keyword));
						if (!scrollPage()) {
							log.info("jobs weren't found");
							continue keywordLoop;
						}
						getLinksFromCurrentPage();
						break;
					} catch (NoSuchWindowException e) {
						log.error("The browser was closed");
						break first; // exiting the loop
					}
				}
				Filter.removeGarbage(jobList);
				cardsAdded = cardsAdded + Storage.addToStorage(jobList, true);
				jobList.clear();
			}
		}

		log.info("Added cards from Monster: " + cardsAdded);
	}

	private String setURL(String location, String keyword) {
		String jobTypeStr = jobType.isBlank() ? "" : "&et=" + jobType;
		String jobRadiusStr = jobRadius.isBlank() ? "" : "&rd=" + jobRadius;
		String jobAgeStr = jobAge.isBlank() ? "" : "&recency=" + jobAge;
		currentURL = blankURL.replaceFirst("keyword", keyword).replaceFirst("location", location)
				.replaceFirst("&et=", jobTypeStr).replaceFirst("&recency=", jobAgeStr)
				.replaceFirst("&rd=", jobRadiusStr).replaceAll(" ", "+");
		return currentURL;
	}

	public void loadSettings() {
		Settings settings = LoadSettings.getSettings("Monster");
		jobType = settings.jobType.contains("Not") ? "" : settings.jobType;
		jobAge = settings.timePosted;
		jobRadius = settings.jobRadius.contains("Not") ? "" : settings.jobRadius;
		keywords = settings.keywords != null ? settings.keywords : Storage.keywords;
		locations = settings.locations != null ? settings.locations : Storage.locations;
		monster_limit = settings.limit == 0 ? Storage.searchLimit : settings.limit;
		run = settings.run;

	}

	public boolean scrollPage() {
		log.info("scrolling page");
		int cardsBefore = 0;
		int cardsAfter = 0;
		List<WebElement> jobCards = null;

		for (short i = 0; i < 2; i++) {
			jobCards = getElements(JOB_TITLE);
			if (jobCards != null) {
				break;
			}
		}
		if (jobCards == null) {
			return false; // if there was no cards on the page, exit
		}
		// we're scrolling the page while new cards available
		do {
			cardsBefore = cardsAfter > 0 ? jobCards.size() : 0;
			WebElement lastCard = jobCards.get(jobCards.size() - 1);
			lastCard.click();
			lastCard.sendKeys(Keys.PAGE_DOWN);
			sleep(500);
			try {
				driver.findElement(By.xpath(NO_MORE_RESULTS));
				log.info("No more cards on the page");
				break;
			} catch (Exception e) {
			}
			jobCards = getElements(JOB_TITLE);
			cardsAfter = jobCards.size();
		} while (cardsAfter < monster_limit && cardsAfter > cardsBefore);
		return true;
	}

	public int getLinksFromCurrentPage() {
		log.info("gathering job cards");
		int counter = 0;
		List<WebElement> cards = null;
		// two attempts to locate cards on the page
		for (short i = 0; i < 2; i++) {
			cards = getElements(JOB_TITLE);
			if (cards != null) {
				break;
			}

		}
		if (cards == null)
			return monster_limit;

		for (WebElement card : cards) {
			for (int i = 3; i > 0; i--) {
				try {
					String href = card.getAttribute("href");
					String jobTitle = card.getText().trim();
					String companyName = card.findElement(By.xpath(COMPANY_NAME)).getText().trim();
					card.click();
					WebElement description = getElement(JOB_DESCRIPTION, WaitType.PRESENCE);
					String jobDescription = description.getText();
					jobList.add(new JobList(jobTitle, companyName, jobDescription, href, currentKeyword));
					counter++;
					break;
				} catch (Exception e) {
					log.error(e.toString());
					log.error("Will try again " + (i-1) + " times");
				}
			}
			log.info("card # " + counter);
			if (counter >= monster_limit) {
				log.info("Searching limit is reached");
				return counter;
			}
		}
		return counter;
	}

}
