package utils;

import static utils.PlaySound.playSound;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import datatypes.JobList;
import jobFinder.Paths;

import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;

public class WriteExcelReport {
	public final static Logger log = LogManager.getLogger("com.jobFinder");

	public static boolean writeReport() {
		LinkedHashMap<String, TreeSet<JobList>> groupedByKeywords = new LinkedHashMap<>();
		LinkedHashMap<String, TreeSet<JobList>> groupedByKeywordsBad = new LinkedHashMap<>();

		if ((Storage.generalJobList.size() == 0) && (Storage.generalBadJobList.size() == 0))
			return false;

		// creating key-value pairs for keywords and jobLists
		for (JobList jobCard : Storage.generalJobList) {
			if (!groupedByKeywords.containsKey(jobCard.searchKeyword)) {
				groupedByKeywords.put(jobCard.searchKeyword, new TreeSet<JobList>());
			}
			groupedByKeywords.get(jobCard.searchKeyword).add(jobCard);
		}
		// write report grouped by keywords
		for (String key : groupedByKeywords.keySet()) {
			String header = "Search keyword: " + key;
			write(header, groupedByKeywords.get(key), true);
		}
		// playing sound
		if (groupedByKeywords.size() > 0 && Storage.playSound)
			playSound();

		// creating key-value pairs for keywords and jobLists
		for (JobList jobCard : Storage.generalBadJobList) {
			if (!groupedByKeywordsBad.containsKey(jobCard.searchKeyword)) {
				groupedByKeywordsBad.put(jobCard.searchKeyword, new TreeSet<JobList>());
			}
			groupedByKeywordsBad.get(jobCard.searchKeyword).add(jobCard);
		}
		// write report grouped by keywords
		for (String key : groupedByKeywordsBad.keySet()) {
			String header = "Search keyword: " + key;
			write(header, groupedByKeywordsBad.get(key), false);
		}
		log.info(Paths.reportFileName + " Successfully created");

		// clearing joblists in case program will run in loop
		Storage.generalJobList.clear();
		Storage.generalBadJobList.clear();
		return true;
	}

	public static void write(String header, Collection<JobList> jobList, boolean good) {
		int lastRow = 0;
		int lastPos = 0;
		if (jobList.size() == 0) {
			return;
		}
		ZipSecureFile.setMinInflateRatio(0.005);
		String todaysSheet = utils.GetDateString.getDate();
		try (FileInputStream fis = new FileInputStream(Paths.reportFileName);
				Workbook workbook = new XSSFWorkbook(fis)) {
			// Retrieve the existing or create a new sheet
			if (!good) {
				todaysSheet = "{BAD} " + utils.GetDateString.getDate();
			}
			Sheet sheet = workbook.getSheet(todaysSheet);
			if (sheet == null) {
				// create new sheet
				sheet = workbook.createSheet(todaysSheet);
				// create new header
				Row row = sheet.createRow(lastRow);
				row.createCell(0).setCellValue("№");
				row.createCell(1).setCellValue("Company");
				row.createCell(2).setCellValue("Position");
				row.createCell(3).setCellValue("Link");
				if (good) {
					row.createCell(4).setCellValue("Resume");
				} else {
					row.createCell(4).setCellValue("Reason");
				}
				// Set the background color
				for (int i = 0; i < 5; i++) {
					setStyle(row.getCell(i), workbook, HorizontalAlignment.CENTER, IndexedColors.YELLOW.getIndex());
				}
				// Set width of the columns
				int width256 = (int) Math
						.floor((10.71f * Units.DEFAULT_CHARACTER_WIDTH + 5) / Units.DEFAULT_CHARACTER_WIDTH * 256);
				sheet.setColumnWidth(1, width256 * 3);
				sheet.setColumnWidth(2, width256 * 3);
				if (!good) {
					sheet.setColumnWidth(4, width256 * 3);
				}

				// Freeze table's header 5 columns and 1 row
				sheet.createFreezePane(5, 1);

			} else { // if sheet exists, get the lastRow and lastPos
				lastRow = sheet.getLastRowNum();
				// get the number from the last cell
				Cell lastCell = sheet.getRow(lastRow).getCell(0);
				if (lastCell == null) {
					lastPos = 1;
				} else if (lastCell.getCellType() == CellType.NUMERIC) {
					lastPos = (int) lastCell.getNumericCellValue();
				}
			}
			// Modify the sheet as needed

			// Adding header if needed
			if (!header.isEmpty()) {
				lastRow++;
				sheet.addMergedRegion(new CellRangeAddress(lastRow, lastRow, 1, 4));
				Row row = sheet.createRow(lastRow);
				Cell timeCell = row.createCell(0);
				timeCell.setCellValue(GetDateString.getTime());
				setStyle(timeCell, workbook, HorizontalAlignment.CENTER, null);
				Cell headerCell = row.createCell(1);
				headerCell.setCellValue(header);
				if (good) {
					setStyle(headerCell, workbook, HorizontalAlignment.CENTER, IndexedColors.PALE_BLUE.getIndex());
				} else {
					setStyle(headerCell, workbook, HorizontalAlignment.CENTER, IndexedColors.RED.getIndex());
				}
			}
			for (JobList item : jobList) {
				lastRow++;
				lastPos++;
				Row row = sheet.createRow(lastRow);
				row.createCell(0).setCellValue(lastPos);
				row.createCell(1).setCellValue(item.companyName);

				// Adding comments to jobTitle
				Cell jobTitleCell = row.createCell(2);
				jobTitleCell.setCellValue(item.jobTitle);
				setComment(jobTitleCell, item.jobDescription);

				// creating a link for apply column
				Cell applyCell = row.createCell(3);
				setStyle(applyCell, workbook, HorizontalAlignment.CENTER, null, item.url);
				applyCell.setCellValue("Apply");

				// link for a custom resume, if available
				Cell resumeCell = row.createCell(4);
				if (good) {
					if (item.refinedResume != null) {
						setStyle(resumeCell, workbook, HorizontalAlignment.CENTER, null, item.refinedResume);
						resumeCell.setCellValue(">>||<<");
					} else {
						resumeCell.setCellValue("N/A");
						setStyle(resumeCell, workbook, HorizontalAlignment.CENTER, null);
					}
				} else {
					resumeCell.setCellValue(item.declineReason);
				}
			}

			// Save the changes back to the file, re-try 10 times
			for (int x = 10; x > 0; x--) {
				try (FileOutputStream fos = new FileOutputStream(Paths.reportFileName)) {
					workbook.write(fos);
					break;
				} catch (Exception e) {
					log.error(e.toString() + "Will retry");
					try {
						Thread.sleep(6000);
					} catch (InterruptedException ex) {
						log.error(ex.toString());
					}
				}
			}

		} catch (FileNotFoundException e) {
			log.error("Report file wasn't found, create a new one");
			createNewFile();
			write(header, jobList, good);
		} catch (Exception e) {
			log.error(e.toString());
		}

	}

	private static void createNewFile() {
		MakeDir.makeDir(Paths.reportFileName);
		try (Workbook workbook = new XSSFWorkbook()) {
			// Create a sheet in the workbook
			workbook.createSheet("First sheet");
			// Save the workbook to a file
			try (FileOutputStream outputStream = new FileOutputStream(Paths.reportFileName)) {
				workbook.write(outputStream);
				log.info("XLSX file created successfully.");
			} catch (IOException e) {
				log.error(e.toString());
			}
		} catch (IOException e) {
			log.error(e.toString());
		}
	}

	private static void setStyle(Cell c, Workbook w, HorizontalAlignment alignment, Short color) {
		CellStyle cellStyle = w.createCellStyle();
		if (color != null) {
			cellStyle.setFillForegroundColor(color);
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}
		cellStyle.setAlignment(alignment);
		c.setCellStyle(cellStyle);
	}

	private static void setStyle(Cell c, Workbook w, HorizontalAlignment alignment, Short color, String url) {
		CellStyle cellStyle = w.createCellStyle();
		if (color != null) {
			cellStyle.setFillForegroundColor(color);
			cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}
		cellStyle.setAlignment(alignment);
		c.setCellStyle(cellStyle);
		Hyperlink link = w.getCreationHelper().createHyperlink(HyperlinkType.URL);
		link.setAddress(url);
		c.setHyperlink(link);
	}

	private static void setComment(Cell cell, String strComment) {
		Drawing<?> drawing = cell.getSheet().createDrawingPatriarch();
		CreationHelper factory = cell.getSheet().getWorkbook().getCreationHelper();
		ClientAnchor anchor = factory.createClientAnchor();
		anchor.setCol1(cell.getColumnIndex());
		anchor.setCol2(cell.getColumnIndex() + 1);
		anchor.setRow1(cell.getRowIndex());
		anchor.setRow2(cell.getRowIndex() + 3);
		Comment comment = drawing.createCellComment(anchor);
		RichTextString str = factory.createRichTextString(strComment);
		comment.setVisible(Boolean.FALSE);
		comment.setString(str);
		cell.setCellComment(comment);
	}
}
