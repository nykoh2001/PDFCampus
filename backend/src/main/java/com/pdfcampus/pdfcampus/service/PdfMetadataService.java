package com.pdfcampus.pdfcampus.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.pdfcampus.pdfcampus.dto.ExtractedTextInfo;
import com.pdfcampus.pdfcampus.entity.Book;
import com.pdfcampus.pdfcampus.entity.Page;
import com.pdfcampus.pdfcampus.entity.RowNum;
import com.pdfcampus.pdfcampus.repository.DetailBookRepository;
import com.pdfcampus.pdfcampus.repository.PageRepository;
import com.pdfcampus.pdfcampus.repository.RowNumRepository;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PdfMetadataService {
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private RowNumRepository rowNumRepository;
    @Autowired
    private DetailBookRepository detailBookRepository;
    @Autowired
    private AmazonS3 s3client;


    public void processPDF(byte[] pdfContent, Integer bid) throws IOException {
        PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfContent));
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        int rowNumber = 1;

        for (int pageNumber = 0; pageNumber < document.getNumberOfPages(); ++pageNumber) {
            Book book = detailBookRepository.findByBid(bid).orElse(null);

            if (book == null) {
                System.out.println("Book with ID: " + bid + " not found.");
                continue;
            }

            // Check if a Page already exists for this book and page number
            Page pageEntity = pageRepository.findByBidAndPageNumber(book.getBid(), pageNumber + 1)
                    .orElse(new Page());

            pageEntity.setBid(book.getBid());
            pageEntity.setPageNumber(pageNumber+1);

            // Render the page to an image and save it to S3
            BufferedImage bim = pdfRenderer.renderImageWithDPI(pageNumber, 300);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(bim, "png", os);
            InputStream is = new ByteArrayInputStream(os.toByteArray());

            String s3Key = book.getBookTitle() + "/page" + pageNumber + ".png";
            String bucketName = "8282book";
            s3client.putObject(new PutObjectRequest(bucketName, s3Key, is, new ObjectMetadata()));

            // Get the URL of the image we just uploaded
            URL s3Url = s3client.getUrl(bucketName, s3Key);
            pageEntity.setPageUrl(s3Url.toString());

            pageEntity = pageRepository.save(pageEntity);

            System.out.println("Page: [" + (pageNumber + 1) + "]");

            PDFTextStripper pdfStripper = new CustomPDFTextStripper(pageEntity, rowNumber);
            pdfStripper.setStartPage(pageNumber+1);
            pdfStripper.setEndPage(pageNumber+1);

            pdfStripper.getText(document);
        }

        document.close();
    }

    private class CustomPDFTextStripper extends PDFTextStripper {
        private static final float Y_TOLERANCE = 2.0f;

        private Page associatedPage;
        private int rowNumber;
        private float lastY = -1;

        public CustomPDFTextStripper(Page associatedPage, int rowNumber) throws IOException {
            super();
            this.associatedPage = associatedPage;
            this.rowNumber = rowNumber;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            StringBuilder fullTextBuilder = new StringBuilder();
            float startX = -1, endX = -1, y = -1;
            for (TextPosition textPosition : textPositions) {
                if (startX == -1 || Math.abs(textPosition.getY() - lastY) <= Y_TOLERANCE) {
                    if (startX == -1) {
                        startX = textPosition.getX();
                        y = textPosition.getY();
                    }
                    fullTextBuilder.append(textPosition.getUnicode());
                    endX = textPosition.getEndX();
                    lastY = y;
                } else {

                    saveRow(fullTextBuilder.toString(), startX, endX, lastY);

                    startX = textPosition.getX();
                    y = textPosition.getY();
                    endX = textPosition.getEndX();
                    fullTextBuilder.setLength(0);
                    fullTextBuilder.append(textPosition.getUnicode());
                    lastY = y;
                }
            }

            if (fullTextBuilder.length() > 0) {
                saveRow(fullTextBuilder.toString(), startX, endX, lastY);
            }
        }

        private void saveRow(String text, float startX, float endX, float y) {
            // Check if a RowNum already exists for this page and row number
            RowNum rowNumEntity = rowNumRepository.findByPidAndRowNumber(associatedPage.getPid(), rowNumber)
                    .orElse(new RowNum());

            rowNumEntity.setPid(associatedPage.getPid());
            rowNumEntity.setRowNumber(rowNumber++);
            rowNumEntity.setRowY(y);

            rowNumRepository.save(rowNumEntity);
            System.out.println("Row: [" + text + "], Position: [" + startX + ", " + endX + ", " + y + "]");
        }
    }
    public ExtractedTextInfo extractTextFromLocation(Integer bid, Integer pageNumber, float startX, float startY, float width, float height) throws IOException {

        // Check if the page exists in the database
        Page page = pageRepository.findByBidAndPageNumber(bid, pageNumber)
                .orElseThrow(() -> new RuntimeException("Page number: " + pageNumber + " for book with ID: " + bid + " not found."));


        // Get the PDF from S3
        S3Object s3Object = s3client.getObject("8282book", bid + ".pdf");
        InputStream objectData = s3Object.getObjectContent();
        byte[] bytes = IOUtils.toByteArray(objectData);

        // Load the PDF document
        PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes));

        // Create a new instance of PDFTextStripperByArea
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();

        // Define a rectangle area with the given startX, startY, width, and height
        Rectangle2D.Float rect = new Rectangle2D.Float(startX, startY, width, height);

        // Add the defined area to the stripper
        stripper.addRegion("region", rect);

        // Extract the text from the defined area
        stripper.extractRegions(document.getPage(pageNumber - 1));
        String text = stripper.getTextForRegion("region");

        // Here we would typically determine the row number and y position
        // but without an actual PDF and logic to calculate the row number, this is not possible
        // So for now, let's set them to some arbitrary values
        int rowNumber = 1;
        float y = startY;

        // Check if the row exists in the database
        RowNum upperRow = rowNumRepository.findFirstByPidAndRowYGreaterThanEqualOrderByRowYAsc(page.getPid(), y);
        RowNum lowerRow = rowNumRepository.findFirstByPidAndRowYLessThanOrderByRowYDesc(page.getPid(), y);

        RowNum rowNum;
        if (upperRow == null) {
            rowNum = lowerRow;
        } else if (lowerRow == null) {
            rowNum = upperRow;
        } else {
            rowNum = Math.abs(upperRow.getRowY() - y) < Math.abs(lowerRow.getRowY() - y) ? upperRow : lowerRow;
        }

        ExtractedTextInfo extractedTextInfo = new ExtractedTextInfo();
        extractedTextInfo.setText(text);
        extractedTextInfo.setRowNumber(rowNum.getRowNumber());  // Now we get the row number from the database
        extractedTextInfo.setYPosition(y);

        document.close();
        objectData.close();

        return extractedTextInfo;
    }
    public List<Integer> getAllPages(Integer bookId) {
        List<Page> pages = pageRepository.findByBid(bookId);
        List<Integer> pageNumbers = new ArrayList<>();

        for (Page page : pages) {
            pageNumbers.add(page.getPageNumber());
        }

        return pageNumbers;
    }

}