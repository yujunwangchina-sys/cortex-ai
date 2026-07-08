package com.cortex.agent.runtime.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件内容解析器
 * 支持: doc/docx, xls/xlsx, ppt/pptx, pdf, txt, md
 * 超长内容自动截断并附加提示
 *
 * @author cortex
 */
@Component
public class FileContentParser
{
    private static final Logger log = LoggerFactory.getLogger(FileContentParser.class);

    /** 默认最大字符数 (约 3 万字, 留余量给 system prompt 和历史消息) */
    private static final int DEFAULT_MAX_CHARS = 30000;

    /** 图片文件扩展名 */
    private static final Set<String> IMAGE_EXTS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg"
    );

    /** 支持的文档扩展名 */
    private static final Set<String> SUPPORTED_EXTS = Set.of(
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".pdf", ".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm"
    );

    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;

    /**
     * 解析文件内容
     *
     * @param filePath  相对路径 (相对于 uploadPath)
     * @param fileName  原始文件名 (用于判断扩展名)
     * @param maxChars  最大字符数限制 (0 = 用默认值)
     * @return 解析后的文本内容, 含截断提示
     */
    public ParsedFile parse(String filePath, String fileName, int maxChars)
    {
        int limit = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        String ext = getExtension(fileName).toLowerCase();

        ParsedFile result = new ParsedFile();
        result.setFileName(fileName);
        result.setExtension(ext);
        result.setImage(IMAGE_EXTS.contains(ext));

        if (IMAGE_EXTS.contains(ext))
        {
            result.setContent("");
            result.setTruncated(false);
            return result;
        }

        if (!SUPPORTED_EXTS.contains(ext))
        {
            result.setContent("[不支持的文件格式: " + ext + "]");
            result.setTruncated(false);
            return result;
        }

        Path absolutePath = Paths.get(uploadPath, filePath);
        if (!Files.exists(absolutePath))
        {
            result.setContent("[文件不存在: " + fileName + "]");
            result.setTruncated(false);
            return result;
        }

        try
        {
            String content = doParse(absolutePath, ext);
            if (content.length() > limit)
            {
                String truncated = content.substring(0, limit);
                truncated += "\n\n[... 文件内容过长, 已截断. 原始长度: "
                        + content.length() + " 字符, 已显示前 " + limit + " 字符 ...]";
                result.setContent(truncated);
                result.setTruncated(true);
                log.info("文件内容截断 [file={}, original={}chars, truncated={}chars]",
                        fileName, content.length(), limit);
            }
            else
            {
                result.setContent(content);
                result.setTruncated(false);
            }
        }
        catch (Exception e)
        {
            log.error("文件解析失败 [file={}]", fileName, e);
            result.setContent("[文件解析失败: " + e.getMessage() + "]");
            result.setTruncated(false);
        }

        return result;
    }

    private String doParse(Path path, String ext) throws Exception
    {
        switch (ext)
        {
            case ".txt":
            case ".md":
            case ".csv":
            case ".json":
            case ".xml":
            case ".html":
            case ".htm":
                return readTextFile(path);

            case ".pdf":
                return parsePdf(path);

            case ".doc":
                return parseDoc(path);

            case ".docx":
                return parseDocx(path);

            case ".xls":
                return parseXls(path);

            case ".xlsx":
                return parseXlsx(path);

            case ".ppt":
                return parsePpt(path);

            case ".pptx":
                return parsePptx(path);

            default:
                return "[不支持的文件格式: " + ext + "]";
        }
    }

    /**
     * PDF 解析 (Apache PDFBox 3.x)
     */
    private String parsePdf(Path path) throws Exception
    {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(path.toFile()))
        {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(doc).trim();
        }
        catch (NoClassDefFoundError e)
        {
            return "[PDFBox 依赖缺失, 无法解析PDF]";
        }
    }

    /**
     * DOC 解析 (Apache POI HWPF)
     */
    private String parseDoc(Path path) throws Exception
    {
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(is))
        {
            return doc.getDocumentText().trim();
        }
    }

    /**
     * DOCX 解析 (Apache POI XWPF)
     */
    private String parseDocx(Path path) throws Exception
    {
        StringBuilder text = new StringBuilder();
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is))
        {
            // 段落
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : doc.getParagraphs())
            {
                text.append(para.getText()).append("\n");
            }
            // 表格
            for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables())
            {
                text.append("\n[表格]\n");
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows())
                {
                    StringBuilder rowText = new StringBuilder();
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells())
                    {
                        rowText.append(cell.getText().trim()).append(" | ");
                    }
                    text.append(rowText).append("\n");
                }
            }
        }
        return text.toString().trim();
    }

    /**
     * XLS 解析 (Apache POI HSSF)
     */
    private String parseXls(Path path) throws Exception
    {
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.hssf.usermodel.HSSFWorkbook wb = new org.apache.poi.hssf.usermodel.HSSFWorkbook(is))
        {
            return parseWorkbook(wb);
        }
    }

    /**
     * XLSX 解析 (Apache POI XSSF)
     */
    private String parseXlsx(Path path) throws Exception
    {
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(is))
        {
            return parseWorkbook(wb);
        }
    }

    private String parseWorkbook(org.apache.poi.ss.usermodel.Workbook wb)
    {
        StringBuilder text = new StringBuilder();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.##");

        for (int i = 0; i < wb.getNumberOfSheets(); i++)
        {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(i);
            text.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");

            for (org.apache.poi.ss.usermodel.Row row : sheet)
            {
                StringBuilder rowText = new StringBuilder();
                for (org.apache.poi.ss.usermodel.Cell cell : row)
                {
                    String cellValue = "";
                    switch (cell.getCellType())
                    {
                        case STRING:
                            cellValue = cell.getStringCellValue().trim();
                            break;
                        case NUMERIC:
                            cellValue = df.format(cell.getNumericCellValue());
                            break;
                        case BOOLEAN:
                            cellValue = String.valueOf(cell.getBooleanCellValue());
                            break;
                        case FORMULA:
                            try { cellValue = df.format(cell.getNumericCellValue()); }
                            catch (Exception e) { cellValue = cell.getCellFormula(); }
                            break;
                        default:
                            break;
                    }
                    rowText.append(cellValue).append(" | ");
                }
                if (rowText.length() > 3)
                {
                    text.append(rowText).append("\n");
                }
            }
            text.append("\n");
        }
        return text.toString().trim();
    }

    /**
     * PPT 解析 (Apache POI HSLF)
     */
    private String parsePpt(Path path) throws Exception
    {
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.hslf.usermodel.HSLFSlideShow ppt = new org.apache.poi.hslf.usermodel.HSLFSlideShow(is))
        {
            return parseSlides(ppt.getSlides());
        }
    }

    /**
     * PPTX 解析 (Apache POI XSLF)
     */
    private String parsePptx(Path path) throws Exception
    {
        try (InputStream is = new FileInputStream(path.toFile());
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(is))
        {
            return parseSlides(ppt.getSlides());
        }
    }

    @SuppressWarnings("rawtypes")
    private String parseSlides(java.util.List slides)
    {
        StringBuilder text = new StringBuilder();
        int slideNum = 0;

        for (Object slide : slides)
        {
            slideNum++;
            text.append("=== Slide ").append(slideNum).append(" ===\n");

            if (slide instanceof org.apache.poi.xslf.usermodel.XSLFSlide)
            {
                org.apache.poi.xslf.usermodel.XSLFSlide xslfSlide = (org.apache.poi.xslf.usermodel.XSLFSlide) slide;
                for (org.apache.poi.xslf.usermodel.XSLFShape shape : xslfSlide.getShapes())
                {
                    if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape)
                    {
                        org.apache.poi.xslf.usermodel.XSLFTextShape ts = (org.apache.poi.xslf.usermodel.XSLFTextShape) shape;
                        text.append(ts.getText()).append("\n");
                    }
                }
            }
            else if (slide instanceof org.apache.poi.hslf.usermodel.HSLFSlide)
            {
                org.apache.poi.hslf.usermodel.HSLFSlide hslfSlide = (org.apache.poi.hslf.usermodel.HSLFSlide) slide;
                for (org.apache.poi.hslf.usermodel.HSLFShape shape : hslfSlide.getShapes())
                {
                    if (shape instanceof org.apache.poi.hslf.usermodel.HSLFTextShape)
                    {
                        org.apache.poi.hslf.usermodel.HSLFTextShape ts = (org.apache.poi.hslf.usermodel.HSLFTextShape) shape;
                        text.append(ts.getText()).append("\n");
                    }
                }
            }
            text.append("\n");
        }
        return text.toString().trim();
    }

    /**
     * Read text file with encoding detection.
     * Handles UTF-8 BOM, UTF-16 BOM, and falls back from UTF-8 to GBK.
     */
    private String readTextFile(Path path) throws Exception
    {
        byte[] bytes = Files.readAllBytes(path);

        // Check BOM
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF)
        {
            // UTF-8 BOM
            return new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE)
        {
            // UTF-16 LE BOM
            return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)
        {
            // UTF-16 BE BOM
            return new String(bytes, 2, bytes.length - 2, java.nio.charset.StandardCharsets.UTF_16BE);
        }

        // No BOM: try UTF-8 first, fall back to GBK
        try
        {
            java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
        catch (Exception e)
        {
            // UTF-8 decode failed, likely GBK encoded (common on Chinese Windows)
            log.debug("UTF-8 decode failed, falling back to GBK [file={}]", path.getFileName());
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
    }
    /**
     * 提取文档内嵌图片(PDF/DOCX/PPTX)
     *
     * @param filePath   文件相对路径(相对于uploadPath)
     * @param fileName   文件名
     * @param documentId 文档ID(用于图片存储子目录)
     * @return 图片相对路径列表(相对于uploadPath)
     */
    public List<String> extractImages(String filePath, String fileName, Long documentId)
    {
        String ext = getExtension(fileName).toLowerCase();
        if (!".pdf".equals(ext) && !".docx".equals(ext) && !".pptx".equals(ext))
        {
            return Collections.emptyList();
        }

        Path absolutePath = Paths.get(uploadPath, filePath);
        if (!Files.exists(absolutePath))
        {
            return Collections.emptyList();
        }

        String imageDir = "knowledge/images/" + documentId;
        Path outputDir = Paths.get(uploadPath, imageDir);
        try
        {
            Files.createDirectories(outputDir);
        }
        catch (Exception e)
        {
            log.error("创建图片目录失败 [dir={}]", outputDir, e);
            return Collections.emptyList();
        }

        List<String> imagePaths = new ArrayList<>();
        try
        {
            switch (ext)
            {
                case ".pdf":
                    imagePaths = extractPdfImages(absolutePath, outputDir, imageDir);
                    break;
                case ".docx":
                    imagePaths = extractDocxImages(absolutePath, outputDir, imageDir);
                    break;
                case ".pptx":
                    imagePaths = extractPptxImages(absolutePath, outputDir, imageDir);
                    break;
            }
        }
        catch (Exception e)
        {
            log.error("图片提取失败 [file={}]", fileName, e);
        }

        log.info("图片提取完成 [file={}, count={}]", fileName, imagePaths.size());
        return imagePaths;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPdfImages(Path pdfPath, Path outputDir, String relativeDir) throws Exception
    {
        List<String> paths = new ArrayList<>();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfPath.toFile()))
        {
            int imgIndex = 0;
            for (org.apache.pdfbox.pdmodel.PDPage page : doc.getPages())
            {
                org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                if (resources == null) continue;
                for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames())
                {
                    org.apache.pdfbox.pdmodel.graphics.PDXObject xobject = resources.getXObject(name);
                    if (xobject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)
                    {
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image =
                                (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobject;
                        imgIndex++;
                        String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + ".png";
                        File imgFile = outputDir.resolve(imgName).toFile();
                        javax.imageio.ImageIO.write(image.getImage(), "png", imgFile);
                        paths.add(relativeDir + "/" + imgName);
                    }
                }
            }
        }
        return paths;
    }

    private List<String> extractDocxImages(Path docxPath, Path outputDir, String relativeDir) throws Exception
    {
        List<String> paths = new ArrayList<>();
        try (InputStream is = new FileInputStream(docxPath.toFile());
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is))
        {
            int imgIndex = 0;
            for (org.apache.poi.xwpf.usermodel.XWPFPictureData picture : doc.getAllPictures())
            {
                imgIndex++;
                String ext = picture.suggestFileExtension();
                if (ext == null || ext.isEmpty()) ext = "png";
                String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext;
                File imgFile = outputDir.resolve(imgName).toFile();
                Files.write(imgFile.toPath(), picture.getData());
                paths.add(relativeDir + "/" + imgName);
            }
        }
        return paths;
    }

    private List<String> extractPptxImages(Path pptxPath, Path outputDir, String relativeDir) throws Exception
    {
        List<String> paths = new ArrayList<>();
        try (InputStream is = new FileInputStream(pptxPath.toFile());
             org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(is))
        {
            int imgIndex = 0;
            for (org.apache.poi.xslf.usermodel.XSLFPictureData picture : ppt.getPictureData())
            {
                imgIndex++;
                String ext = picture.suggestFileExtension();
                if (ext == null || ext.isEmpty()) ext = "png";
                String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext;
                File imgFile = outputDir.resolve(imgName).toFile();
                Files.write(imgFile.toPath(), picture.getData());
                paths.add(relativeDir + "/" + imgName);
            }
        }
        return paths;
    }

    /**
     * 解析文档并按图片实际位置插入 [imgNNN] 标记
     * PDF按页提取，DOCX按文档元素顺序遍历，PPTX按幻灯片提取
     */
    public ParsedFileWithImages parseWithImages(String filePath, String fileName, Long documentId)
    {
        String ext = getExtension(fileName).toLowerCase();
        ParsedFileWithImages result = new ParsedFileWithImages();
        result.setFileName(fileName);
        result.setExtension(ext);

        Path absolutePath = Paths.get(uploadPath, filePath);
        if (!Files.exists(absolutePath))
        {
            result.setContent("[文件不存在: " + fileName + "]");
            result.setImagePaths(Collections.emptyList());
            return result;
        }

        if (!".pdf".equals(ext) && !".docx".equals(ext) && !".pptx".equals(ext))
        {
            ParsedFile parsed = parse(filePath, fileName, Integer.MAX_VALUE);
            result.setContent(parsed.getContent());
            result.setImagePaths(Collections.emptyList());
            return result;
        }

        String imageDir = "knowledge/images/" + documentId;
        Path outputDir = Paths.get(uploadPath, imageDir);
        try { Files.createDirectories(outputDir); } catch (Exception e) { log.error("创建图片目录失败", e); }

        List<String> imagePaths = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();
        int imgCounter = 0;

        try
        {
            if (".pdf".equals(ext))
            {
                try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(absolutePath.toFile()))
                {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    for (int p = 0; p < pdfDoc.getNumberOfPages(); p++)
                    {
                        stripper.setStartPage(p + 1);
                        stripper.setEndPage(p + 1);
                        fullText.append(stripper.getText(pdfDoc));
                        org.apache.pdfbox.pdmodel.PDPage page = pdfDoc.getPage(p);
                        org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                        if (resources != null)
                        {
                            for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames())
                            {
                                org.apache.pdfbox.pdmodel.graphics.PDXObject xobj = resources.getXObject(name);
                                if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)
                                {
                                    imgCounter++;
                                    String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + ".png";
                                    javax.imageio.ImageIO.write(
                                            ((org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobj).getImage(),
                                            "png", outputDir.resolve(imgName).toFile());
                                    imagePaths.add(imageDir + "/" + imgName);
                                    fullText.append("[img").append(String.format("%03d", imgCounter)).append("]");
                                }
                            }
                        }
                    }
                }
            }
            else if (".docx".equals(ext))
            {
                try (InputStream is = new FileInputStream(absolutePath.toFile());
                     org.apache.poi.xwpf.usermodel.XWPFDocument docx = new org.apache.poi.xwpf.usermodel.XWPFDocument(is))
                {
                    for (org.apache.poi.xwpf.usermodel.IBodyElement element : docx.getBodyElements())
                    {
                        if (element instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph)
                        {
                            org.apache.poi.xwpf.usermodel.XWPFParagraph para = (org.apache.poi.xwpf.usermodel.XWPFParagraph) element;
                            for (org.apache.poi.xwpf.usermodel.XWPFRun run : para.getRuns())
                            {
                                if (run.getEmbeddedPictures() != null)
                                {
                                    for (org.apache.poi.xwpf.usermodel.XWPFPicture pic : run.getEmbeddedPictures())
                                    {
                                        imgCounter++;
                                        org.apache.poi.xwpf.usermodel.XWPFPictureData picData = pic.getPictureData();
                                        String imgExt = picData.suggestFileExtension();
                                        if (imgExt == null || imgExt.isEmpty()) imgExt = "png";
                                        String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + "." + imgExt;
                                        Files.write(outputDir.resolve(imgName), picData.getData());
                                        imagePaths.add(imageDir + "/" + imgName);
                                        fullText.append("[img").append(String.format("%03d", imgCounter)).append("]");
                                    }
                                }
                                fullText.append(run.text());
                            }
                            fullText.append("\n");
                        }
                        else if (element instanceof org.apache.poi.xwpf.usermodel.XWPFTable)
                        {
                            org.apache.poi.xwpf.usermodel.XWPFTable table = (org.apache.poi.xwpf.usermodel.XWPFTable) element;
                            fullText.append("\n[表格]\n");
                            for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows())
                            {
                                for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells())
                                {
                                    fullText.append(cell.getText().trim()).append(" | ");
                                }
                                fullText.append("\n");
                            }
                        }
                    }
                }
            }
            else if (".pptx".equals(ext))
            {
                try (InputStream is = new FileInputStream(absolutePath.toFile());
                     org.apache.poi.xslf.usermodel.XMLSlideShow pptx = new org.apache.poi.xslf.usermodel.XMLSlideShow(is))
                {
                    int slideNum = 0;
                    for (org.apache.poi.xslf.usermodel.XSLFSlide slide : pptx.getSlides())
                    {
                        slideNum++;
                        fullText.append("=== Slide ").append(slideNum).append(" ===\n");
                        for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes())
                        {
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape)
                            {
                                fullText.append(((org.apache.poi.xslf.usermodel.XSLFTextShape) shape).getText()).append("\n");
                            }
                            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFPictureShape)
                            {
                                org.apache.poi.xslf.usermodel.XSLFPictureData picData =
                                        ((org.apache.poi.xslf.usermodel.XSLFPictureShape) shape).getPictureData();
                                if (picData != null)
                                {
                                    imgCounter++;
                                    String imgExt = picData.suggestFileExtension();
                                    if (imgExt == null || imgExt.isEmpty()) imgExt = "png";
                                    String imgName = java.util.UUID.randomUUID().toString().replace("-", "") + "." + imgExt;
                                    Files.write(outputDir.resolve(imgName), picData.getData());
                                    imagePaths.add(imageDir + "/" + imgName);
                                    fullText.append("[img").append(String.format("%03d", imgCounter)).append("]");
                                }
                            }
                        }
                        fullText.append("\n");
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("文档解析+图片提取失败 [file={}]", fileName, e);
        }

        result.setContent(fullText.toString().trim());
        result.setImagePaths(imagePaths);
        log.info("文档解析+图片提取完成 [file={}, images={}]", fileName, imagePaths.size());
        return result;
    }
    private String getExtension(String fileName)
    {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0)
        {
            return fileName.substring(lastDot);
        }
        return "";
    }

    /**
     * 判断文件是否为图片
     */
    public boolean isImage(String fileName)
    {
        return IMAGE_EXTS.contains(getExtension(fileName).toLowerCase());
    }

    /**
     * 判断文件是否支持解析
     */
    public boolean isSupported(String fileName)
    {
        String ext = getExtension(fileName).toLowerCase();
        return IMAGE_EXTS.contains(ext) || SUPPORTED_EXTS.contains(ext);
    }

    /**
     * 解析结果
     */
    public static class ParsedFile
    {
        private String fileName;
        private String extension;
        private String content;
        private boolean truncated;
        private boolean image;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public boolean isTruncated() { return truncated; }
        public void setTruncated(boolean truncated) { this.truncated = truncated; }
        public boolean isImage() { return image; }
        public void setImage(boolean image) { this.image = image; }
    }

    public static class ParsedFileWithImages
    {
        private String fileName;
        private String extension;
        private String content;
        private List<String> imagePaths;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImagePaths() { return imagePaths; }
        public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths; }
    }
}
