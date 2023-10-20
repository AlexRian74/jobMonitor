package pages;

import java.time.Duration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Point;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import datatypes.WaitType;

public class BaseDriver implements AutoCloseable{

	WebDriver driver = null;
	WebDriverWait wait = null;
	JavascriptExecutor js;
	Actions actions;
	protected static final Logger log = LogManager.getLogger("com.jobFinder");
	final String CONTAINS_TEXT = "//*[contains(text(), 'value')]";

	public void setUp(String url, int windowPosition) {
		driver = new EdgeDriver();
		driver.manage().window().setPosition(new Point(windowPosition, 0));
		js = (JavascriptExecutor) driver;
		wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		driver.manage().window().maximize();
		driver.get(url);

	}

	public void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public List<WebElement> getChildElements(String childrenXPath, WebElement parent) {
		List<WebElement> elements = null;
		short count = 3;
		boolean flag = true;
		while (flag && count > 0) {
			try {
				elements = parent.findElements(By.xpath(childrenXPath));
				flag = false;
			} catch (Exception e) {
				count--;
			}
		}
		if (count == 0) {
			return null;
		}
		return elements;
	}

	public WebElement getChildElement(String childrenXPath, WebElement parent) {
		WebElement element = null;
		short count = 3;
		boolean flag = true;
		while (flag && count > 0) {
			try {
				element = parent.findElement(By.xpath(childrenXPath));
				flag = false;
			} catch (Exception e) {
				count--;
			}
		}
		if (count == 0) {
			return null;
		}
		return element;

	}

	public WebElement getElement(String xpath, WaitType w) throws NoSuchWindowException{
		WebElement element = null;

		try {
			switch (w) {
			case PRESENCE:
				element = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
				break;
			case CLICKABLE:
				element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
				break;
			}
		} catch (Exception e) {
			if(e instanceof NoSuchWindowException) {
				throw (NoSuchWindowException) e;
			}
		}
		return element;
	}

	public List<WebElement> getElements(String xpath) {
		List<WebElement> element = null;
		try {
			element = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(xpath)));
		} catch (Exception e) {
		}
		return element;
	}

	public void typeText(WebElement e, String s) {
		if (e == null)
			return;
		char[] array = s.toCharArray();
		js.executeScript("arguments[0].focus();", e);
		for (char ch : array) {
			e.sendKeys("" + ch);
			sleep(20);
		}
	}

	public void eraseText(WebElement e) {
		if (e == null)
			return;
		String text = (String) js.executeScript("return arguments[0].value;", e);
		for (int i = 0; i < text.length(); i++) {
			js.executeScript("arguments[0].focus();", e);
			e.sendKeys(Keys.DELETE);
			e.sendKeys(Keys.BACK_SPACE);
		}
	}

	public String removeTags(String str) {
		if (str == null) {
			return "";
		}
		return str.replaceAll("<li[^>]*>", "-").replaceAll("<br>", "\n")
				.replaceAll("<style[^>]*>[\\s\\S]*?</style>|<[^>]*>", "").replaceAll("(?m)^\\s+", "")
				.replaceAll("\\s+$", "").replaceAll("&nbsp;", "").trim();
	}

	public void scrollPage(String cardsXPath, int limit) {
		log.info("Scrolling the page");
		int cardsBefore = 0;
		int cardsAfter = 0;
		List<WebElement> jobCards = null;

		for (short i = 0; i < 2; i++) {
			jobCards = getElements(cardsXPath);
			if (jobCards != null) {
				break;
			}
		}
		if (jobCards == null) {
			return; // if there was no cards on the page, exit
		}
		// we're scrolling the page while new cards available
		do {
			//sometimes we get stale element reference here
			for (short j = 2; j > 0; j--) {
				try {
					cardsBefore = jobCards.size();
					WebElement lastCard = jobCards.get(cardsBefore - 1);
					js.executeScript("arguments[0].scrollIntoView(true);", lastCard);
					break;
				} catch (Exception e) {
					jobCards = getElements(cardsXPath);
					log.error(e);
				}
			}
			sleep(2000);
			jobCards = getElements(cardsXPath);
			cardsAfter = jobCards.size();
			// scrolling only up to limit cards
		} while (cardsAfter < limit && cardsAfter > cardsBefore);

	}

	public void close() {
		if (driver == null) {
			return;
		}
		driver.quit();
	}

	public boolean getPage(String url) {

		for (int i = 3; i > 0; i--) {
			try {
				driver.get(url);
				return true;
			} catch (TimeoutException e) {
				log.error(e.toString());
				log.error("Will try again " + (i-1) + " times");
			}
		}
		return false;
	}

}
