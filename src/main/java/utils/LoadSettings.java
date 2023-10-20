package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.*;

import datatypes.Settings;
import jobFinder.Main;
import jobFinder.Paths;

public class LoadSettings {
	private static Workbook workbook;
	private static Sheet sheet;
	public final static Logger log = LogManager.getLogger("com.jobFinder");

	public static Settings getSettings(String moduleName) {
		String[] values;
		switch (moduleName) {
		case "General":
			values = NamedRanges.general;
			break;
		case "LinkedIn":
			values = NamedRanges.linkedIn;
			break;
		case "Glassdoor":
			values = NamedRanges.glassdoor;
			break;
		case "Indeed":
			values = NamedRanges.indeed;
			break;
		case "Monster":
			values = NamedRanges.monster;
			break;
		case "Ziprecruiter":
			values = NamedRanges.ziprecruiter;
			break;
		case "Google":
			values = NamedRanges.google;
			break;
		case "Gpt":
			values = NamedRanges.gpt;
			break;
		default:
			return null;
		}
		LinkedHashMap<String, String> dic = getPairs(values);

		Settings settings = new Settings();
		for (String key : dic.keySet()) {
			if (key.contains("locations")) {
				String tmp = dic.get(key);
				settings.locations = tmp.isBlank() ? null : tmp.split(",");
			} else if (key.contains("keywords")) {
				String tmp = dic.get(key);
				settings.keywords = tmp.isBlank() ? null : tmp.split(",");
			} else if (key.contains("switch")) {
				settings.run = dic.get(key).equals("on") ? true : false;
			} else if (key.contains("limit")) {
				settings.limit = getDigits(dic.get(key));
			} else if (key.contains("delay")) {
				settings.delay = getDigits(dic.get(key));
			} else if (key.contains("level")) {
				settings.jobLevel = dic.get(key);
			} else if (key.contains("posted")) {
				settings.timePosted = dic.get(key);
			} else if (key.contains("sorted")) {
				settings.sortBy = dic.get(key);
			} else if (key.contains("type")) {
				settings.jobType = dic.get(key);
			} else if (key.contains("sortby")) {
				settings.sortBy = dic.get(key);
			} else if (key.contains("radius")) {
				settings.jobRadius = dic.get(key);
			} else if (key.contains("by")) {
				settings.postedBy = dic.get(key);
			}
		}
		return settings;
	}

	public static int getDigits(String tmp) {
		try {
			return Integer.parseInt(tmp);
		} catch (Exception e) {
			return 0;
		}
	}

	// takes cell names and return key-value pair cellName:cellValue
	public static LinkedHashMap<String, String> getPairs(String[] cellNames) {
		LinkedHashMap<String, String> array = new LinkedHashMap<>();
		try {
			// Create a FileInputStream to read the Excel file
			FileInputStream fis = new FileInputStream(Paths.settingsFileName);
			// Initialize a workbook using XSSFWorkbook for xlsx files
			workbook = new XSSFWorkbook(fis);
			sheet = workbook.getSheet("Settings");

			for (String cell : cellNames) {
				array.put(cell, getValue(cell).trim());
			}
			fis.close();
			workbook.close();
		} catch (FileNotFoundException e) {
			log.info("Settings file wasn't found. Trying to create it.");
			createNewSettingsFile();
			System.exit(0);
		} catch (IOException e) {
			log.error(e.toString());
			System.exit(0);
		}
		return array;
	}

	private static void createNewSettingsFile() {
		File file = new File(Paths.settingsFileName);
		if (file.exists())
			return;
		// Load template from resources
		InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("Settings.xlsx");
		if (inputStream == null)
			return;

		try {
			Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			inputStream.close();
			log.info("Settings file has been created here: " + Paths.settingsFileName
					+ ", fill it up and re-launch the app.");
		} catch (IOException e) {
			log.error(e.toString());
		}

	}

	// returns the value of a cell using its named range
	private static String getValue(String name) {
		Name namedRange = workbook.getName(name);
		if (namedRange != null) {
			// Get the cell range reference of the named range
			String cellRange = namedRange.getRefersToFormula();
			// Split the cell range reference into individual cell references
			String[] cellReferences = cellRange.split(":");
			// since we're using merged cells, the value in the first cell
			CellReference cellRef = new CellReference(cellReferences[0]);
			Row row = sheet.getRow(cellRef.getRow());
			Cell cell = row.getCell(cellRef.getCol());
			if (cell.getCellType() == CellType.NUMERIC) {
				return cell.toString().replaceAll("\\.(.*)", "");
			}
			return cell.toString();
		} else {
			System.out.println("Named range not found.");
		}
		return null;
	}

}
