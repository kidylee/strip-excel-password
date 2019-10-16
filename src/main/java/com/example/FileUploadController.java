package com.example;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Controller
public class FileUploadController {

    @PostMapping("/")
    public ResponseEntity<Resource> handleFileUpload(@RequestParam("file") MultipartFile file) throws IOException, ParserConfigurationException {

        ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream());

        File output = File.createTempFile("temp",null);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try {
            ZipEntry entry = null;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    String content = IOUtils.toString(zipInputStream, Charset.defaultCharset());
                    if (content.contains("<workbookProtection") || content.contains("<sheetProtection")) {
                        try {
                            System.out.printf("File: %s Size %d  Modified on %TD %n", entry.getName(), entry.getSize(), new Date(entry.getTime()));

                            Document doc = builder.parse(IOUtils.toInputStream(content, Charset.defaultCharset()));
                            Node protection = doc.getElementsByTagName("workbookProtection").item(0);
                            if (protection != null) protection.getParentNode().removeChild(protection);
                            protection = doc.getElementsByTagName("sheetProtection").item(0);
                            if (protection != null) protection.getParentNode().removeChild(protection);


                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            Source xmlSource = new DOMSource(doc);
                            Result outputTarget = new StreamResult(outputStream);
                            TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
                            InputStream is = new ByteArrayInputStream(outputStream.toByteArray());

                            zos.putNextEntry(new ZipEntry(entry.getName()));
                            byte[] buf = new byte[1024];
                            int len;
                            while ((len = is.read(buf)) > 0) {
                                zos.write(buf, 0, len);
                            }
                            zos.closeEntry();

                        } catch (SAXException e) {
                            e.printStackTrace();
                        } catch (TransformerConfigurationException e) {
                            e.printStackTrace();
                        } catch (TransformerException e) {
                            e.printStackTrace();
                        }
                    } else {
                        simpleCopy(IOUtils.toInputStream(content, Charset.defaultCharset()), zos, entry);
                    }

                } else {
                    simpleCopy(zipInputStream, zos, entry);
                }

            }
        } finally {
            zipInputStream.close();
            zos.close();
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + file.getResource().getFilename() + "\"").body(new FileSystemResource(output));
        }


    }

    private static void simpleCopy(InputStream inputStream, ZipOutputStream zos, ZipEntry entry) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entry.getName());
        zos.putNextEntry(zipEntry);

        byte[] buf = new byte[1024];
        int len;
        while ((len = inputStream.read(buf)) > 0) {
            zos.write(buf, 0, len);
        }
    }
}
